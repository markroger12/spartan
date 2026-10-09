package dev.aegisac.paper.output;
import dev.aegisac.common.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class WebhookWorkerTest {
    @TempDir Path directory; ConfigService config; WebhookWorker worker;
    @BeforeEach void setup() throws Exception {
        config=new ConfigService(new ConfigurationLoader(directory)); config.reload();
        Files.writeString(directory.resolve("webhooks.yml"),"config-version: 1\nenabled: true\nurl: https://discord.com/api/webhooks/123/"+"a".repeat(24)+"\nretry-limit: 1\n"); config.reload();
    }
    @Test void successfulSendDisablesMentionsAndUsesNoExternalNetwork() throws Exception {
        var body=new CompletableFuture<String>(); worker=new WebhookWorker(config::current,(policy,payload)->{ body.complete(payload); return new WebhookWorker.Response(204,0); });
        assertTrue(worker.offer(config.current().generation(),"ALERT","@everyone \"x\""));
        String payload=body.get(5,TimeUnit.SECONDS); assertTrue(payload.contains("\"allowed_mentions\":{\"parse\":[]}")); assertTrue(payload.contains("\\\"x\\\""));
        assertFalse(worker.offer(config.current().generation(),"DIAGNOSTIC","hidden"));
    }
    @Test void transientFailureRetriesWithSpacingAndStopsAtLimit() throws Exception {
        var attempts=new AtomicInteger(); var end=new CountDownLatch(2); var times=new java.util.concurrent.CopyOnWriteArrayList<Long>();
        worker=new WebhookWorker(config::current,(policy,payload)->{ times.add(System.nanoTime()); attempts.incrementAndGet(); end.countDown(); return new WebhookWorker.Response(503,0); });
        worker.offer(config.current().generation(),"ALERT","fixture"); assertTrue(end.await(5,TimeUnit.SECONDS)); worker.close(); assertTrue(worker.awaitClosed(5000));
        assertEquals(2,attempts.get()); assertTrue(times.get(1)-times.get(0)>=900_000_000L);
    }
    @Test void permanentFailureDoesNotRetryAndStaleGenerationDoesNotSend() throws Exception {
        var called=new CountDownLatch(1); var count=new AtomicInteger(); worker=new WebhookWorker(config::current,(policy,payload)->{ count.incrementAndGet(); called.countDown(); return new WebhookWorker.Response(400,0); });
        worker.offer(config.current().generation()-1,"ALERT","stale"); worker.offer(config.current().generation(),"ALERT","current");
        assertTrue(called.await(5,TimeUnit.SECONDS)); worker.close(); assertTrue(worker.awaitClosed(5000)); assertEquals(1,count.get());
    }
    @Test void boundedQueueDropsOverflowAndCloseInterruptsBlockedTransport() throws Exception {
        var started=new CountDownLatch(1); worker=new WebhookWorker(config::current,(policy,payload)->{ started.countDown(); new CountDownLatch(1).await(); return new WebhookWorker.Response(204,0); });
        worker.offer(config.current().generation(),"ALERT","first"); assertTrue(started.await(5,TimeUnit.SECONDS));
        for(int i=0;i<64;i++) assertTrue(worker.offer(config.current().generation(),"ALERT","queued"));
        assertFalse(worker.offer(config.current().generation(),"ALERT","overflow")); assertTrue(worker.dropped()>0); worker.close(); assertTrue(worker.awaitClosed(5000));
    }
    @AfterEach void cleanup() throws Exception { if(worker!=null) { worker.close(); assertTrue(worker.awaitClosed(5000)); } }
}
