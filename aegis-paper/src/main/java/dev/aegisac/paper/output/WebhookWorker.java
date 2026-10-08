package dev.aegisac.paper.output;
import dev.aegisac.common.config.*;
import dev.aegisac.common.output.OutputRecord;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
/** Fixed queue, finite retries, global request spacing, no redirects and no response-body retention. */
public final class WebhookWorker implements AutoCloseable {
    public record Response(int status,long retryMillis) { }
    @FunctionalInterface interface Transport extends AutoCloseable { Response send(OutputSettings.Webhook policy,String body) throws Exception; @Override default void close() { } }
    private record Message(long generation,String kind,String text) { }
    private final ArrayBlockingQueue<Message> queue=new ArrayBlockingQueue<>(64);
    private final Supplier<ConfigSnapshot> config; private final Transport transport; private final Thread worker;
    private volatile boolean closed; private long nextAttempt; private boolean attemptScheduled;
    private final AtomicLong dropped=new AtomicLong(),failed=new AtomicLong(),sent=new AtomicLong();
    public WebhookWorker(Supplier<ConfigSnapshot> config) { this(config,http()); }
    WebhookWorker(Supplier<ConfigSnapshot> config,Transport transport) {
        this.config=config; this.transport=transport; worker=new Thread(this::run,"AegisAC-webhooks"); worker.setDaemon(true); worker.start();
    }
    private static Transport http() {
        // The shared client is created on first send, never during plugin enable.
        return new Transport() {
            private HttpClient client;
            @Override public void close() { if(client!=null) client.shutdownNow(); }
            public Response send(OutputSettings.Webhook p,String body) throws Exception {
                if(client==null) client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
                var request=HttpRequest.newBuilder(URI.create(p.url())).timeout(Duration.ofMillis(p.timeoutMillis())).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var response=client.send(request,HttpResponse.BodyHandlers.discarding()); long retry=0;
                try { retry=Math.min(60000,Math.max(0,(long)(Double.parseDouble(response.headers().firstValue("Retry-After").orElse("0"))*1000))); } catch(NumberFormatException ignored) { }
                return new Response(response.statusCode(),retry);
            }
        };
    }
    private static boolean enabled(OutputSettings.Webhook s,String kind) { return s.enabled()&&switch(kind) { case "ALERT"->s.alerts(); case "DIAGNOSTIC"->s.alerts()&&s.diagnostics(); case "PUNISHMENT"->s.punishments(); case "STARTUP"->s.startup(); case "ERROR"->s.errors(); default->false; }; }
    public synchronized boolean offer(long generation,String kind,String text) {
        if(!enabled(config.get().output().webhook(),kind)) return false;
        if(closed||!queue.offer(new Message(generation,kind,OutputRecord.clean(text,1900)))) { dropped.incrementAndGet(); return false; } return true;
    }
    private void run() {
        try {
            while(!closed) {
                var m=queue.poll(100,TimeUnit.MILLISECONDS); if(m==null) continue;
                for(int attempt=0;!closed;attempt++) {
                    long delay=attemptScheduled?nextAttempt-System.nanoTime():0; if(delay>0) TimeUnit.NANOSECONDS.sleep(delay);
                    var c=config.get(); var p=c.output().webhook(); if(c.generation()!=m.generation||!enabled(p,m.kind)) break;
                    Response response;
                    try { response=transport.send(p,"{\"allowed_mentions\":{\"parse\":[]},\"content\":"+OutputRecord.quote(m.text)+"}"); }
                    catch(InterruptedException interrupted) { throw interrupted; }
                    catch(Exception failure) { response=new Response(503,0); }
                    attemptScheduled=true; nextAttempt=System.nanoTime()+Math.max(p.intervalMillis(),response.retryMillis())*1_000_000L;
                    if(response.status()>=200&&response.status()<300) { sent.incrementAndGet(); break; }
                    if(attempt>=p.retries()||response.status()!=429&&response.status()<500) { failed.incrementAndGet(); break; }
                }
            }
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        finally { dropped.addAndGet(queue.size()); queue.clear(); transport.close(); }
    }
    public long dropped() { return dropped.get(); } public long failed() { return failed.get(); } public long sent() { return sent.get(); }
    @Override public synchronized void close() { closed=true; worker.interrupt(); }
    public boolean awaitClosed(long ms) throws InterruptedException { worker.join(ms); return !worker.isAlive(); }
}
