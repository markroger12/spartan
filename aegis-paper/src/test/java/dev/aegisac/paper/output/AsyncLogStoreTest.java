package dev.aegisac.paper.output;
import dev.aegisac.common.config.*;
import dev.aegisac.common.output.OutputRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class AsyncLogStoreTest {
    @TempDir Path directory; ConfigService config; AsyncLogStore store; UUID uuid=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        config=new ConfigService(new ConfigurationLoader(directory)); config.reload();
        Files.writeString(directory.resolve("logging.yml"),"config-version: 1\nenabled: true\nplain-text: true\nmaximum-file-bytes: 4096\n");
        Files.writeString(directory.resolve("storage.yml"),"config-version: 1\nmaximum-rows: 100\n"); config.reload();
        store=new AsyncLogStore(directory,config::current);
    }
    OutputRecord record(int i) { return new OutputRecord(i,uuid,"SpeedA","DIAGNOSTIC",config.current().generation(),Map.of("player","A'lice","index",Integer.toString(i),"evidence","test".repeat(40))); }
    List<String> query() throws Exception { var reply=new CompletableFuture<List<String>>(); assertTrue(store.query(uuid,reply::complete)); return reply.get(10,TimeUnit.SECONDS); }
    @Test void sqliteBatchesRetainRowsRotateTextAndQueryEscapedRecords() throws Exception {
        for(int i=0;i<150;i++) assertTrue(store.offer(record(i)));
        var rows=query(); assertEquals(10,rows.size()); assertTrue(rows.getFirst().contains("149")); assertTrue(rows.getFirst().contains("A'lice"));
        try(var connection=new org.sqlite.JDBC().connect("jdbc:sqlite:"+directory.resolve("logs/evidence.sqlite"),new Properties());var statement=connection.createStatement();var count=statement.executeQuery("SELECT COUNT(*) FROM evidence")) { assertTrue(count.next()); assertEquals(100,count.getInt(1)); }
        assertTrue(Files.size(directory.resolve("logs/evidence.jsonl"))<=4096); assertTrue(Files.size(directory.resolve("logs/evidence.previous.jsonl"))<=4096);
        assertEquals(150,store.written()); assertEquals(0,store.failed());
    }
    @Test void generationAndDisabledDiagnosticsCannotWriteQueuedRecords() throws Exception {
        var old=record(1); config.reload(); assertTrue(store.offer(old)); assertTrue(query().isEmpty());
        Files.writeString(directory.resolve("logging.yml"),"config-version: 1\nenabled: true\ndiagnostics: false\n"); config.reload();
        assertTrue(store.offer(record(2))); assertTrue(query().isEmpty()); assertEquals(0,store.written());
    }
    @Test void unwritableDestinationReportsFailureWithoutBlockingCaller() throws Exception {
        Files.writeString(directory.resolve("logs"),"not a directory"); assertTrue(store.offer(record(1))); assertTrue(query().isEmpty()); assertTrue(store.failed()>0);
    }
    @Test void closedWriterRejectsNewWorkAndDrainsAlreadyAcceptedWrites() throws Exception {
        assertTrue(store.offer(record(1))); store.close(); assertTrue(store.awaitClosed(5000)); assertFalse(store.offer(record(2))); assertFalse(store.query(uuid,ignored->{})); assertEquals(1,store.written());
    }
    @AfterEach void cleanup() throws Exception { store.close(); assertTrue(store.awaitClosed(5000)); }
}
