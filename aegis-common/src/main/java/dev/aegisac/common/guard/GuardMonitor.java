package dev.aegisac.common.guard;

import dev.aegisac.api.check.GuardSnapshot;
import dev.aegisac.api.player.*;
import dev.aegisac.common.check.*;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.combat.CombatGeometry;
import dev.aegisac.common.config.*;
import dev.aegisac.common.connection.TransactionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.common.world.*;
import java.util.*;

/** Worker-owned Phase 6 state; all platform reads arrive as immutable owner observations. */
public final class GuardMonitor {
    private Set<String> manualBypasses=Set.of();
    public void manualBypasses(Set<String> values) { manualBypasses=Set.copyOf(values); }
    private Set<String> combined(Set<String> values) { if(manualBypasses.isEmpty()) return values; var result=new HashSet<>(values); result.addAll(manualBypasses); return Set.copyOf(result); }

    private record Block(int x,int y,int z) { }
    private record Dig(long time,Block block) { }
    private final GuardDispatcher dispatcher=new GuardDispatcher();
    public void output(java.util.function.Consumer<dev.aegisac.common.output.Detection> sink) { dispatcher.output(sink); }
    private final EnumMap<GuardId,RateWindow> rates=new EnumMap<>(GuardId.class);
    private final ArrayDeque<Dig> completed=new ArrayDeque<>();
    private long generation=-1,baseline,lastTime,digTime,useTime,teleportTime;
    private boolean seen,digSeen,useSeen,sequenceSeen,abilitiesSeen,flightAllowed;
    private int sequence,menu=-1,teleport=-1,towerStreak;
    private Block digging,lastPlace;
    private long placeTime;
    private GuardSettings settings=GuardSettings.defaults();
    public GuardMonitor() {
        for(var id:List.of(GuardId.PacketSpamA,GuardId.MovementSpamA,GuardId.InteractionSpamA,GuardId.PayloadSpamA,GuardId.FastPlaceA,GuardId.FastUseA)) rates.put(id,new RateWindow());
    }
    public void reset(long now,String reason) {
        baseline=now; seen=digSeen=useSeen=sequenceSeen=abilitiesSeen=false;
        menu=teleport=-1; towerStreak=0; digging=lastPlace=null; completed.clear();
        rates.values().forEach(RateWindow::clear); dispatcher.reset(reason,settings);
    }
    public void process(ConfigSnapshot config,PacketFrame frame,MovementSnapshot before,
            WorldView.State world,GuardOwnerObservation attributes,OwnerObservation owner,
            ConnectionSnapshot connection,ServerTickHealth.Snapshot health,BedrockStatus edition,
            TransactionTracker.Acknowledgement acknowledgement,int entityId) {
        long now=frame.observedNanos();
        if(generation!=config.generation()) { generation=config.generation(); settings=config.guard(); reset(now,"CONFIGURATION_CHANGED"); }
        if(seen&&now-lastTime<0) reset(now,"OBSERVATION_ORDER_UNKNOWN");
        lastTime=now; seen=true;
        if(frame.packet().kind()==PacketKind.WORLD_RESET||frame.packet().kind()==PacketKind.TELEPORT) {
            reset(now,"TELEPORT_OR_WORLD_RESET");
            if(frame.packet() instanceof Teleport t) { teleport=t.id(); teleportTime=now; }
            return;
        }
        Set<String> reasons=gates(config,frame,owner,connection,health,edition);
        Set<String> bypasses=combined(owner!=null&&config.exemptions().enabled()&&config.exemptions().permissions()?owner.bypasses():Set.of());
        var values=new EnumMap<GuardId,Double>(GuardId.class);
        if(frame.direction()==PacketDirection.OUTBOUND) {
            if(frame.packet() instanceof Inventory i) {
                if(i.action()==InventoryAction.OPEN) menu=i.window();
                else if(i.action()==InventoryAction.CLOSE&&i.window()==menu) menu=-1;
            }
            if(frame.packet() instanceof Abilities a&&a.serverAuthority()) { abilitiesSeen=true; flightAllowed=a.flightAllowed(); }
            if(frame.packet() instanceof HeldItem) useSeen=false;
            return;
        }
        rate(GuardId.PacketSpamA,now,values);
        values.put(GuardId.BadPacketsA,flag(!finitePacket(frame.packet())));
        if(frame.packet() instanceof Movement m) {
            rate(GuardId.MovementSpamA,now,values);
            if(m.position()&&finite(m.x(),m.y(),m.z())) values.put(GuardId.InvalidPositionA,flag(Math.abs(m.x())>30_000_000||Math.abs(m.y())>30_000_000||Math.abs(m.z())>30_000_000));
            if(m.rotation()&&Float.isFinite(m.pitch())) values.put(GuardId.InvalidPitchA,Math.max(0,Math.abs((double)m.pitch())-90));
            if(m.position()&&before.positionKnown()&&finite(m.x(),m.y(),m.z(),before.x(),before.y(),before.z())&&fresh(now,before.observedNanos(),250)) {
                if(menu>0) values.put(GuardId.InventoryMoveA,Math.hypot(Math.hypot(m.x()-before.x(),m.z()-before.z()),m.y()-before.y()));
            }
            if(teleport!=-1&&now-teleportTime>10_000_000_000L) teleport=-1;
            if(m.position()&&teleport!=-1) values.put(GuardId.PacketOrderA,1.0);
        } else if(frame.packet() instanceof Vehicle v&&finite(v.x(),v.y(),v.z())) {
            values.put(GuardId.InvalidPositionA,flag(Math.abs(v.x())>30_000_000||Math.abs(v.y())>30_000_000||Math.abs(v.z())>30_000_000));
            if(Float.isFinite(v.pitch())) values.put(GuardId.InvalidPitchA,Math.max(0,Math.abs((double)v.pitch())-90));
        } else if(frame.packet() instanceof HeldItem h) {
            values.put(GuardId.InvalidSlotA,flag(h.slot()<0||h.slot()>8)); useSeen=false;
        } else if(frame.packet() instanceof Inventory i) {
            if(i.action()==InventoryAction.CLICK) {
                if(menu>=0&&i.window()!=0) values.put(GuardId.ImpossibleInventoryA,flag(i.window()!=menu));
                values.put(GuardId.SlotSpoofA,flag(i.slot()<0&&i.slot()!=-999));
            } else if(i.action()==InventoryAction.CLOSE&&i.window()==menu) menu=-1;
        } else if(frame.packet() instanceof EntityAction a) {
            if(menu>0&&a.action().equals("START_SPRINTING")) values.put(GuardId.InventorySprintA,1.0);
        } else if(frame.packet() instanceof Abilities a) {
            if(abilitiesSeen) values.put(GuardId.ImpossibleClientStateA,flag(a.flying()&&!flightAllowed));
        } else if(frame.packet() instanceof Payload p) {
            rate(GuardId.PayloadSpamA,now,values); values.put(GuardId.PayloadSizeA,(double)p.bytes());
        } else if(frame.packet() instanceof Interaction i) {
            rate(GuardId.InteractionSpamA,now,values);
            values.put(GuardId.InvalidEntityInteractionA,flag((entityId>=0&&i.entityId()==entityId)||i.targetPresent()&&!finite(i.targetX(),i.targetY(),i.targetZ())));
        } else if(frame.packet() instanceof Timing t) {
            if(acknowledgement!=null) values.put(GuardId.TransactionA,flag(!acknowledgement.sampled()));
            if(t.type()==TimingKind.TELEPORT&&acknowledgement!=null&&acknowledgement.sampled()&&t.id()==teleport) teleport=-1;
        } else if(frame.packet() instanceof UseItem u) {
            useSeen=true; useTime=now; rate(GuardId.FastUseA,now,values);
            sequence(frame,u.sequence(),values);
        } else if(frame.packet() instanceof BlockAction b) {
            boolean blockDig=Set.of("START_DIGGING","CANCELLED_DIGGING","FINISHED_DIGGING").contains(b.action());
            // The legacy air-use sentinel is not a block placement or an invalid face.
            boolean airUse=b.placement()&&b.face()==255&&!frame.protocol().modernInventory();
            if(airUse) { useSeen=true; useTime=now; }
            else if(b.placement()||blockDig) {
                sequence(frame,b.sequence(),values);
                Block target=new Block(b.x(),b.y(),b.z());
                if(b.placement()) {
                    rate(GuardId.FastPlaceA,now,values);
                    values.put(GuardId.ImpossiblePlaceA,flag(b.face()<0||b.face()>5||!finite(b.cursorX(),b.cursorY(),b.cursorZ())
                            ||b.cursorX()<0||b.cursorX()>1||b.cursorY()<0||b.cursorY()>1||b.cursorZ()<0||b.cursorZ()>1));
                } else if(b.action().equals("START_DIGGING")) { digging=target; digTime=now; digSeen=true; }
                else {
                    boolean retained=digSeen&&fresh(now,digTime,30_000);
                    values.put(GuardId.InvalidDigA,flag(!retained||!target.equals(digging)));
                    digSeen=false; digging=null;
                    if(b.action().equals("FINISHED_DIGGING")) {
                        while(!completed.isEmpty()&&now-completed.peekFirst().time()>=1_000_000_000L) completed.removeFirst();
                        if(completed.size()>=128) { completed.removeFirst(); reasons.add("DIG_HISTORY_TRIMMED"); }
                        completed.addLast(new Dig(now,target));
                        if(rates.get(GuardId.PacketSpamA).ready(now)) values.put(GuardId.NukerA,(double)completed.stream().map(Dig::block).distinct().count());
                    }
                }
                geometry(frame,b,before,world,attributes,reasons,values);
                if(b.placement()) {
                    boolean below=before.positionKnown()&&finite(before.x(),before.y(),before.z())&&b.y()<before.y()&&before.y()-b.y()<=2
                            &&Math.abs(b.x()+.5-before.x())<1.5&&Math.abs(b.z()+.5-before.z())<1.5;
                    boolean quick=lastPlace!=null&&fresh(now,placeTime,150);
                    values.put(GuardId.ScaffoldA,flag(below&&quick&&values.getOrDefault(GuardId.RotationPlacementA,0.0)>0));
                    towerStreak=below&&quick&&lastPlace.x()==b.x()&&lastPlace.z()==b.z()&&(long)b.y()-lastPlace.y()==1?Math.min(4,towerStreak+1):0;
                    values.put(GuardId.TowerA,flag(towerStreak>=3));
                    lastPlace=target; placeTime=now;
                }
            } else if(b.action().equals("RELEASE_USE_ITEM")) {
                if(!useSeen||fresh(now,useTime,30_000)) values.put(GuardId.UseOrderA,flag(!useSeen));
                useSeen=false;
            }
        }
        for(var entry:values.entrySet()) {
            var gates=new HashSet<>(reasons); var policy=settings.category(entry.getKey());
            if(now-baseline<policy.graceMillis()*1_000_000L) gates.add("TIMING_GRACE");
            if(owner==null||!fresh(now,owner.observedNanos(),policy.maximumDelayMillis())) gates.add("OWNER_STATE_UNKNOWN");
            if(connection==null||connection.processingDelayNanos()>policy.maximumDelayMillis()*1_000_000L) gates.add("PROCESSING_DELAY_UNKNOWN");
            dispatcher.evaluate(entry.getKey(),entry.getValue(),frame.sequence(),now,generation,settings,gates,bypasses);
        }
    }
    private void rate(GuardId id,long now,Map<GuardId,Double> values) {
        var rate=rates.get(id); int count=rate.add(now); if(rate.ready(now)) values.put(id,(double)count);
    }
    private void sequence(PacketFrame frame,int current,Map<GuardId,Double> values) {
        // Block/use sequence numbers were introduced in Java 1.19 (protocol 759).
        if(!frame.protocol().known()||frame.protocol().id()<759) return;
        if(sequenceSeen) values.put(GuardId.InvalidSequenceA,flag(current-sequence<=0));
        sequenceSeen=true; sequence=current;
    }
    private void geometry(PacketFrame frame,BlockAction b,MovementSnapshot movement,WorldView.State state,
            GuardOwnerObservation attributes,Set<String> reasons,Map<GuardId,Double> values) {
        long now=frame.observedNanos(); int age=settings.categories().get("world").maximumDelayMillis();
        reasons.add("UNACKNOWLEDGED_WORLD"); reasons.add("BLOCK_ACTION_ACCEPTANCE_UNKNOWN");
        if(!movement.positionKnown()||!finite(movement.x(),movement.y(),movement.z())||!fresh(now,movement.observedNanos(),age)
                ||attributes==null||!fresh(now,attributes.observedNanos(),age)) { reasons.add("BLOCK_GEOMETRY_UNAVAILABLE"); return; }
        Vec3 eye=new Vec3(movement.x(),movement.y()+attributes.eyeHeight(),movement.z());
        Aabb block=new Aabb(b.x(),b.y(),b.z(),(double)b.x()+1,(double)b.y()+1,(double)b.z()+1);
        values.put(GuardId.BlockReachA,Math.max(0,CombatGeometry.distance(eye,block)-attributes.blockRange()));
        if(b.face()>=0&&b.face()<=5) {
            boolean opposite=switch(b.face()) { case 0->eye.y()>block.minY()+.001; case 1->eye.y()<block.maxY()-.001;
                case 2->eye.z()>block.minZ()+.001; case 3->eye.z()<block.maxZ()-.001;
                case 4->eye.x()>block.minX()+.001; default->eye.x()<block.maxX()-.001; };
            values.put(GuardId.DirectionA,flag(opposite));
        }
        if(b.placement()&&movement.rotationKnown()&&Float.isFinite(movement.yaw())&&Float.isFinite(movement.pitch()))
            values.put(GuardId.RotationPlacementA,flag(!Double.isFinite(CombatGeometry.ray(eye,CombatGeometry.direction(movement.yaw(),movement.pitch()),block.expand(.03,.03,.03)))));
        var world=state==null?null:state.snapshot();
        if(world==null||state.closed()||world.revision()!=state.revision()||!fresh(now,world.capturedNanos(),age)) { reasons.add("WORLD_CAPTURE_UNAVAILABLE"); return; }
        world.uncertainty().forEach(u->reasons.add(u.name()));
        Vec3 target=new Vec3(b.x()+.5,b.y()+.5,b.z()+.5);
        var corridor=new Aabb(Math.min(eye.x(),target.x()),Math.min(eye.y(),target.y()),Math.min(eye.z(),target.z()),Math.max(eye.x(),target.x()),Math.max(eye.y(),target.y()),Math.max(eye.z(),target.z()));
        if(!world.coverage().contains(corridor)||!world.coverage().contains(block)) { reasons.add("OUTSIDE_WORLD_CAPTURE"); return; }
        Vec3 difference=target.subtract(eye); double length=Math.sqrt(difference.lengthSquared());
        boolean occluded=false,liquid=false;
        if(length>1e-9) {
            Vec3 direction=new Vec3(difference.x()/length,difference.y()/length,difference.z()/length);
            for(var sample:world.blocks()) {
                if(sample.volume().intersects(block)) { liquid|=sample.surfaces().contains(BlockSample.Surface.WATER)||sample.surfaces().contains(BlockSample.Surface.LAVA); continue; }
                for(var shape:sample.shapes()) if(CombatGeometry.ray(eye,direction,shape)<length-.001) occluded=true;
            }
        }
        values.put(GuardId.GhostHandA,flag(occluded));
        if(!b.placement()&&b.action().equals("FINISHED_DIGGING")) values.put(GuardId.LiquidInteractionA,flag(liquid));
    }
    private static Set<String> gates(ConfigSnapshot config,PacketFrame frame,OwnerObservation owner,ConnectionSnapshot connection,ServerTickHealth.Snapshot health,BedrockStatus edition) {
        Set<String> reasons=new HashSet<>(); reasons.add("CLIENT_STATE_UNCONFIRMED");
        if(edition!=BedrockStatus.JAVA) reasons.add("UNKNOWN_EDITION");
        if(!frame.protocol().known()) reasons.add("UNKNOWN_PROTOCOL");
        if(connection==null||connection.uncertain()||connection.lossEpoch()!=connection.processedEpoch()) reasons.add("CONNECTION_UNCERTAIN");
        if(!health.known()||!fresh(frame.observedNanos(),health.observedNanos(),250)||health.tps()<18||health.intervalNanos()>250_000_000) reasons.add("SERVER_HEALTH_UNKNOWN");
        if(owner!=null) {
            if(owner.vehicle()||owner.gliding()) reasons.add("SPECIAL_MOVEMENT");
            if(config.exemptions().enabled()) {
                if(config.exemptions().worlds().contains(owner.world())) reasons.add("WORLD_EXEMPT");
                if(config.exemptions().creativeOrSpectator()&&owner.creativeOrSpectator()) reasons.add("GAMEMODE");
                if(config.exemptions().flight()&&owner.flight()) reasons.add("FLIGHT_ALLOWED");
            }
        }
        return reasons;
    }
    public static boolean finitePacket(NormalizedPacket packet) {
        return switch(packet) {
            case Movement m -> (!m.position()||finite(m.x(),m.y(),m.z()))&&(!m.rotation()||finite(m.yaw(),m.pitch()));
            case Vehicle v -> finite(v.x(),v.y(),v.z(),v.yaw(),v.pitch());
            case BlockAction b -> !b.placement()||finite(b.cursorX(),b.cursorY(),b.cursorZ());
            case UseItem u -> finite(u.yaw(),u.pitch());
            case Interaction i -> !i.targetPresent()||finite(i.targetX(),i.targetY(),i.targetZ());
            case Input i -> finite(i.forward(),i.sideways());
            default -> true;
        };
    }
    private static boolean finite(double... values) { for(double value:values) if(!Double.isFinite(value)) return false; return true; }
    private static boolean fresh(long now,long time,int millis) { return now-time>=0&&now-time<=millis*1_000_000L; }
    private static double flag(boolean value) { return value?1.0:0.0; }
    public GuardSnapshot snapshot() { return dispatcher.snapshot(generation); }
}
