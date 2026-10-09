package dev.aegisac.tools;

import dev.aegisac.api.player.*;
import dev.aegisac.common.bedrock.IdentityObservation;
import dev.aegisac.common.check.*;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.config.ConfigSnapshot;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.physics.*;
import dev.aegisac.common.trace.*;
import dev.aegisac.common.world.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Synthetic inputs, not recordings from a vanilla client. Numeric conformance has separate reference tests. */
public final class FixtureCorpus {
    public static final List<String> LEGITIMATE=List.of("walking","sprinting","jumping","ice","slime","honey","water","ladder",
            "elytra","velocity","high-ping","lag-spike","teleport","piston","bedrock-translation");
    public static final List<String> SUSPICIOUS=List.of("speed-burst","invalid-coordinate");
    private static final ClientProtocol PROTOCOL=new ClientProtocol(774,"1.21.11",true,true,true,true);
    private FixtureCorpus() { }
    public static WorldSnapshot world(String scenario,long time,long revision) {
        var floor=new Aabb(-64,0,-64,64,1,64);var area=new Aabb(-64,1,-64,64,16,64);
        var surface=switch(scenario) { case "ice"->BlockSample.Surface.ICE;case "slime"->BlockSample.Surface.SLIME;default->BlockSample.Surface.SOLID; };
        var blocks=new ArrayList<BlockSample>();blocks.add(new BlockSample(floor,List.of(floor),Set.of(surface)));
        var special=switch(scenario) { case "honey"->BlockSample.Surface.HONEY;case "water"->BlockSample.Surface.WATER;
            case "ladder"->BlockSample.Surface.CLIMBABLE;case "piston"->BlockSample.Surface.PISTON;default->null; };
        if(special!=null)blocks.add(new BlockSample(area,List.of(),Set.of(special)));
        var context=scenario.equals("elytra")?new MotionContext(.1,.42,-1,-1,false,Set.of(Uncertainty.ELYTRA)):MotionContext.NORMAL;
        return new WorldSnapshot(new UUID(0,1),revision,time,new Aabb(-64,-4,-64,64,20,64),blocks,List.of(),context,Set.of());
    }
    public static List<TraceEntry> entries(String scenario,ConfigSnapshot config) {
        if(!LEGITIMATE.contains(scenario)&&!SUSPICIOUS.contains(scenario))throw new IllegalArgumentException("Unknown fixture");
        String fingerprint=AnalysisFingerprint.of(config);var result=new ArrayList<TraceEntry>();
        var state=new PhysicsEngine.State(new Vec3(0,1,0),Vec3.ZERO,true,1.8);var engine=new PhysicsEngine();
        long sequence=0,time=0,revision=0,epoch=0;
        for(int tick=0;tick<80;tick++) {
            time+=scenario.equals("lag-spike")&&tick==20?1_000_000_000:50_000_000;
            NormalizedPacket packet;PacketDirection direction=PacketDirection.INBOUND;
            if(scenario.equals("high-ping")&&(tick==2||tick==14)) {
                packet=new Timing(TimingKind.PING,42,0,true);direction=tick==2?PacketDirection.OUTBOUND:PacketDirection.INBOUND;
            } else if(scenario.equals("velocity")&&tick==20) { packet=new Impulse(false,1,.2,.3,0);direction=PacketDirection.OUTBOUND; }
            else if(scenario.equals("teleport")&&tick==20) { packet=new Teleport(7,0,1,0,0,0,0,0,0,0);direction=PacketDirection.OUTBOUND;revision++; }
            else if(scenario.equals("teleport")&&tick==21)packet=new Timing(TimingKind.TELEPORT,7,0,true);
            else {
                double z=scenario.equals("speed-burst")?tick%2*8:state.feet().z();
                if(scenario.equals("invalid-coordinate")&&tick==25)z=Double.NaN;
                packet=new Movement(true,true,state.feet().x(),state.feet().y(),z,0,0,state.grounded(),false);
            }
            if(scenario.equals("lag-spike")&&tick==20) { epoch++;revision++; }
            var world=world(scenario,time,revision);
            var status=scenario.equals("bedrock-translation")?BedrockStatus.BEDROCK:BedrockStatus.JAVA;
            var identity=new IdentityObservation(config.generation(),0,new EditionSnapshot(status,"FIXTURE","UNKNOWN","UNKNOWN","synthetic",time,Set.of()));
            result.add(new TraceEntry(new PacketFrame(++sequence,epoch,time,direction,PROTOCOL,packet),time,epoch,config.generation(),fingerprint,0,1,
                    new WorldView.State(revision,world,false),new OwnerObservation(time,"world",false,false,false,scenario.equals("elytra"),Set.of()),
                    new dev.aegisac.common.combat.CombatOwnerObservation(time,1.62,3),new dev.aegisac.common.guard.GuardOwnerObservation(time,1.62,4.5),identity,
                    new ServerTickHealth.Snapshot(time,scenario.equals("lag-spike")&&tick==20?1_000_000_000:50_000_000,20,true),false));
            state=engine.step(PhysicsProfile.JAVA_1_21_11,state,new PhysicsEngine.Input(1,0,0,scenario.equals("sprinting"),false,scenario.equals("jumping")&&tick==3),world).state();
            if(packet instanceof Teleport) { revision++;state=new PhysicsEngine.State(new Vec3(0,1,0),Vec3.ZERO,true,1.8); }
            if(scenario.equals("lag-spike")&&tick==20)revision++; // Worker loss reset invalidates again.
            if(packet instanceof Impulse impulse)state=new PhysicsEngine.State(state.feet(),new Vec3(impulse.x(),impulse.y(),impulse.z()),false,1.8);
        }
        return List.copyOf(result);
    }
    public static void write(Path directory,ConfigSnapshot config)throws IOException {
        for(var group:Map.of("legitimate",LEGITIMATE,"suspicious",SUSPICIOUS).entrySet()) {
            Path folder=directory.resolve(group.getKey());Files.createDirectories(folder);
            for(String name:group.getValue())try(var writer=new TraceFile.Writer(folder.resolve(name+".aegistrace"))) {
                for(var entry:entries(name,config))if(!writer.append(entry))throw new IOException("Fixture exceeded trace budget");
                writer.finish(0,"SYNTHETIC_FIXTURE");
            }
        }
    }
}
