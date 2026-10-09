package dev.aegisac.common.trace;

import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.world.*;
import dev.aegisac.common.check.ServerTickHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TraceTest {
    @TempDir Path directory;
    private TraceEntry entry(long sequence,NormalizedPacket packet) {
        return new TraceEntry(new PacketFrame(sequence,0,sequence*50_000_000,PacketDirection.INBOUND,ClientProtocol.UNKNOWN,packet),sequence*50_000_000,
                0,1,"fixture",0,1,new WorldView.State(0,null,false),null,null,null,null,ServerTickHealth.Snapshot.unknown(),false);
    }
    private List<NormalizedPacket> packets() {
        return List.of(new Movement(true,true,Double.NaN,1,2,Float.POSITIVE_INFINITY,0,false,false),new Timing(TimingKind.PING,7,0,true),
                new Interaction(1,"ATTACK","MAIN_HAND",false,0,0,0),new BlockAction(true,1,2,3,1,4,"PLACE","MAIN_HAND",.1f,.2f,.3f),
                new UseItem("OFF_HAND",2,3,4),new HeldItem(1),new Inventory(InventoryAction.CLICK,1,2,3,4,"PICKUP"),new EntityAction(1,"START_SPRINTING",0),
                new Abilities(false,true,false,false,.1f,.2f),new Vehicle(1,2,3,4,5),new Input(1,0,true,false,false,false,1),
                new RotationCorrection(1,2,false,true),new Teleport(1,2,3,4,5,6,0,0,0,0),new Impulse(false,1,.2,.3,.4),
                new Effect(1,"SPEED",1,20,false),new EntityStatus(1,2),new BlockAcknowledgement(1),new Action(PacketKind.WORLD_RESET,"RESET"),
                new Payload(20,"minecraft:brand","vanilla"),new EntitySpawn(1,new UUID(0,1),true,0,1,0),new EntityMove(1,true,false,0,1,2,3),
                new EntityDimensionsUnknown(1),new EntityRemove(List.of(1,2)),Other.INSTANCE);
    }
    @Test void everyNormalizedVariantRoundTripsIncludingNonfiniteDiagnosticValues()throws Exception {
        var seen=new HashSet<Class<?>>();
        for(var packet:packets()) { var input=entry(1,packet);assertEquals(input,TraceCodec.decode(TraceCodec.encode(input)));seen.add(packet.getClass()); }
        assertEquals(Set.of(NormalizedPacket.class.getPermittedSubclasses()),seen);
    }
    @Test void rejectsTruncationUnknownTypeOversizedStringsAndTrailingGarbage()throws Exception {
        byte[] good=TraceCodec.encode(entry(1,Other.INSTANCE));
        assertThrows(IOException.class,()->TraceCodec.decode(Arrays.copyOf(good,good.length-1)));
        byte[] extra=Arrays.copyOf(good,good.length+1);assertThrows(IOException.class,()->TraceCodec.decode(extra));
        byte[] unknown=good.clone();unknown[5]='x';assertThrows(IOException.class,()->TraceCodec.decode(unknown));
        assertThrows(IOException.class,()->TraceCodec.encode(entry(1,new Payload(1,"x","a".repeat(4097)))));
        assertThrows(IOException.class,()->TraceCodec.decode(new byte[TraceCodec.MAX_ENTRY_BYTES+1]));
        assertThrows(IOException.class,()->TraceCodec.decode(new byte[]{8,127,127,127,127}));
    }
    @Test void sealedFilesDetectCorruptionTruncationUnsupportedVersionAndOrder()throws Exception {
        Path path=directory.resolve("good");
        try(var writer=new TraceFile.Writer(path)) { assertTrue(writer.append(entry(1,Other.INSTANCE)));writer.finish(0,"TEST"); }
        assertEquals(1,TraceFile.read(path).entries().size());
        byte[] good=Files.readAllBytes(path),bad=good.clone();bad[bad.length-1]^=1;Files.write(directory.resolve("bad"),bad);
        assertThrows(IOException.class,()->TraceFile.read(directory.resolve("bad")));
        Files.write(directory.resolve("short"),Arrays.copyOf(good,good.length-32));assertThrows(IOException.class,()->TraceFile.read(directory.resolve("short")));
        bad=good.clone();bad[7]=99;Files.write(directory.resolve("version"),bad);assertThrows(IOException.class,()->TraceFile.read(directory.resolve("version")));
        try(var writer=new TraceFile.Writer(directory.resolve("order"))) { writer.append(entry(2,Other.INSTANCE));writer.append(entry(1,Other.INSTANCE));writer.finish(0,"TEST"); }
        assertThrows(IOException.class,()->TraceFile.read(directory.resolve("order")));
        assertThrows(FileAlreadyExistsException.class,()->new TraceFile.Writer(path));
    }
    @Test void writerEnforcesRecordLimitAndRecorderDrainsOnClose()throws Exception {
        Path path=directory.resolve("limit");
        try(var writer=new TraceFile.Writer(path)) {
            for(int i=1;i<=TraceFile.MAX_RECORDS;i++)assertTrue(writer.append(entry(i,Other.INSTANCE)));
            assertFalse(writer.append(entry(TraceFile.MAX_RECORDS+1,Other.INSTANCE)));writer.finish(1,"RECORD_LIMIT");
        }
        assertEquals(2000,TraceFile.read(path).entries().size());assertEquals(1,TraceFile.read(path).dropped());
        var metrics=new PacketMetrics();var recorder=new TraceRecorder(directory.resolve("async"),metrics,error->fail(error));
        recorder.offer(entry(1,Other.INSTANCE));recorder.offer(entry(2,Other.INSTANCE));recorder.close();
        var recording=TraceFile.read(recorder.completion().get(10,TimeUnit.SECONDS));
        assertEquals(2,recording.entries().size());assertEquals(0,recording.dropped());assertFalse(recorder.accepting());
    }
    @Test void existingDirectoryBudgetIsEnforcedWithoutDeletingFiles()throws Exception {
        for(int i=0;i<8;i++)Files.writeString(directory.resolve("existing"+i),"retained");
        var recorder=new TraceRecorder(directory,new PacketMetrics(),error->{});
        assertThrows(java.util.concurrent.ExecutionException.class,()->recorder.completion().get(10,TimeUnit.SECONDS));
        try(var files=Files.list(directory)) { assertEquals(8,files.count()); }
    }
    @Test void sessionCaptureStopsOnReloadAndDisconnectWithoutCapturingConfigurationDocuments()throws Exception {
        var config=new java.util.concurrent.atomic.AtomicReference<>(new dev.aegisac.common.config.ConfigurationLoader(directory.resolve("config")).load(1));
        var metrics=new PacketMetrics();var recorder=new TraceRecorder(directory.resolve("session"),metrics,error->fail(error));
        try(var registry=new dev.aegisac.common.player.PlayerRegistry()) {
            var data=registry.join(new UUID(0,1),"PrivatePlayer",0);data.configure(config.get().pipeline());data.configureMetrics(metrics);
            data.configureChecks(config::get,ServerTickHealth.Snapshot::unknown);data.configureTrace(recorder);
            data.process(entry(1,Other.INSTANCE).frame(),50_000_000);
            config.set(new dev.aegisac.common.config.ConfigurationLoader(directory.resolve("config")).load(2));
            data.process(entry(2,Other.INSTANCE).frame(),100_000_000);
            assertFalse(recorder.accepting());
        }
        Path file=recorder.completion().get(10,TimeUnit.SECONDS);var recording=TraceFile.read(file);
        assertEquals(1,recording.entries().size());assertEquals(2,metrics.analysis().batches());
        String bytes=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains("PrivatePlayer"));assertFalse(bytes.contains("webhooks"));
    }
}
