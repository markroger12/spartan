package dev.aegisac.paper.output;
import dev.aegisac.common.config.ConfigSnapshot;
import dev.aegisac.common.config.OutputSettings;
import dev.aegisac.common.output.OutputRecord;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
/** Single daemon owns SQLite/files. Caller threads only perform bounded nonblocking queue offers. */
public final class AsyncLogStore implements AutoCloseable {
    private sealed interface Job permits Write,Read { }
    private record Write(OutputRecord record) implements Job { }
    private record Read(UUID uuid,Consumer<List<String>> reply) implements Job { }
    private final ArrayBlockingQueue<Job> queue=new ArrayBlockingQueue<>(512);
    private final Path directory; private final Supplier<ConfigSnapshot> config;
    private final Thread worker; private volatile boolean closed; private volatile long shutdownDeadline; private Connection database;
    private final AtomicLong dropped=new AtomicLong(),failed=new AtomicLong(),written=new AtomicLong();
    public AsyncLogStore(Path directory,Supplier<ConfigSnapshot> config) {
        this.directory=directory.resolve("logs"); this.config=config;
        worker=new Thread(this::run,"AegisAC-log-writer"); worker.setDaemon(true); worker.start();
    }
    public synchronized boolean offer(OutputRecord record) { if(closed||!queue.offer(new Write(record))) { dropped.incrementAndGet(); return false; } return true; }
    public synchronized boolean query(UUID uuid,Consumer<List<String>> reply) { if(closed||!queue.offer(new Read(uuid,reply))) { dropped.incrementAndGet(); return false; } return true; }
    public long dropped() { return dropped.get(); } public long failed() { return failed.get(); } public long written() { return written.get(); }
    private Path path(String name) throws java.io.IOException {
        Files.createDirectories(directory);
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(directory.getParent())) throw new java.io.IOException("Symlink log directory");
        Path path=directory.resolve(name); if(Files.isSymbolicLink(path)) throw new java.io.IOException("Symlink log file"); return path;
    }
    private Connection database() throws Exception {
        if(database!=null) return database;
        Path file=path("evidence.sqlite"); path("evidence.sqlite-journal"); path("evidence.sqlite-wal"); path("evidence.sqlite-shm");
        // Explicit driver avoids dependence on another plugin's JDBC service registration.
        var driver=(java.sql.Driver)Class.forName("org.sqlite.JDBC").getConstructor().newInstance();
        database=driver.connect("jdbc:sqlite:"+file,new Properties());
        try(var s=database.createStatement()) {
            s.execute("PRAGMA busy_timeout=1000"); s.execute("PRAGMA max_page_count=65536");
            s.execute("CREATE TABLE IF NOT EXISTS evidence (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, uuid TEXT NOT NULL, check_id TEXT NOT NULL, type TEXT NOT NULL, record TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS evidence_player ON evidence(uuid,id)");
        }
        return database;
    }
    private void run() {
        try {
            while(!closed||!queue.isEmpty()&&System.nanoTime()<shutdownDeadline) {
                Job job=queue.poll(100,TimeUnit.MILLISECONDS); if(job==null) continue;
                if(job instanceof Read read) { read(read); continue; }
                var batch=new ArrayList<OutputRecord>(32); batch.add(((Write)job).record());
                while(batch.size()<32&&queue.peek() instanceof Write) batch.add(((Write)queue.poll()).record());
                try { write(batch); } catch(Exception|LinkageError failure) { failed.addAndGet(batch.size()); closeDatabase(); }
            }
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); dropped.addAndGet(queue.size()); queue.clear(); }
        finally { dropped.addAndGet(queue.size()); queue.clear(); closeDatabase(); }
    }
    private void write(List<OutputRecord> records) throws Exception {
        var c=config.get(); var settings=c.output().logging(); if(!settings.enabled()) return;
        var batch=records.stream().filter(r->r.generation()==c.generation()&&(settings.diagnostics()||!r.type().equals("DIAGNOSTIC"))).toList();
        if(batch.isEmpty()) return;
        if(settings.sqlite()) {
            var db=database(); db.setAutoCommit(false);
            try(var insert=db.prepareStatement("INSERT INTO evidence(timestamp,uuid,check_id,type,record) VALUES(?,?,?,?,?)")) {
                for(var r:batch) { insert.setLong(1,r.timestamp()); insert.setString(2,r.uuid().toString()); insert.setString(3,r.check()); insert.setString(4,r.type()); insert.setString(5,r.json()); insert.addBatch(); }
                insert.executeBatch();
                try(var trim=db.prepareStatement("DELETE FROM evidence WHERE id <= (SELECT id FROM evidence ORDER BY id DESC LIMIT 1 OFFSET ?)")) { trim.setInt(1,settings.maximumRows()); trim.executeUpdate(); }
                db.commit();
            } catch(Exception failure) { db.rollback(); throw failure; } finally { db.setAutoCommit(true); }
        }
        if(settings.text()) for(var r:batch) {
            byte[] line=(r.json()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(line.length>settings.maximumFileBytes()) { dropped.incrementAndGet(); continue; }
            Path current=path("evidence.jsonl"),previous=path("evidence.previous.jsonl");
            if(Files.exists(current)&&Files.size(current)+line.length>settings.maximumFileBytes()) Files.move(current,previous,StandardCopyOption.REPLACE_EXISTING);
            Files.write(current,line,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }
        if(settings.sqlite()||settings.text()) written.addAndGet(batch.size());
    }
    private void read(Read request) {
        List<String> result=new ArrayList<>();
        try {
            var s=config.get().output().logging();
            if(s.enabled()&&s.sqlite()) try(var query=database().prepareStatement("SELECT substr(record,1,4096) FROM evidence WHERE uuid=? ORDER BY id DESC LIMIT 10")) {
                query.setString(1,request.uuid().toString()); try(var rows=query.executeQuery()) { while(rows.next()) result.add(rows.getString(1)); }
            }
        } catch(Exception|LinkageError failure) { failed.incrementAndGet(); closeDatabase(); }
        try { request.reply().accept(List.copyOf(result)); } catch(RuntimeException ignored) { failed.incrementAndGet(); }
    }
    private void closeDatabase() { if(database!=null) try { database.close(); } catch(SQLException ignored) { } finally { database=null; } }
    @Override public synchronized void close() { shutdownDeadline=System.nanoTime()+2_000_000_000L; closed=true; /* Drain the bounded queue on its worker; never join on the server tick. */ }
    public boolean awaitClosed(long milliseconds) throws InterruptedException { worker.join(milliseconds); return !worker.isAlive(); }
}
