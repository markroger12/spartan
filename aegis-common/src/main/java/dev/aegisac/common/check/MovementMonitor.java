package dev.aegisac.common.check;
import dev.aegisac.api.check.MovementChecksSnapshot;
import dev.aegisac.api.player.*;
import dev.aegisac.common.config.*;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import java.util.*;
/** Lifecycle and health gates are independent of movement evaluators. No platform calls or enforcement here. */
public final class MovementMonitor {
    private Set<String> manualBypasses=Set.of();
    public void manualBypasses(Set<String> values) { manualBypasses=Set.copyOf(values); }
    private Set<String> combined(Set<String> values) { if(manualBypasses.isEmpty()) return values; var result=new HashSet<>(values); result.addAll(manualBypasses); return Set.copyOf(result); }

    private final MovementDispatcher dispatcher=new MovementDispatcher();
    public void output(java.util.function.Consumer<dev.aegisac.common.output.Detection> sink) { dispatcher.output(sink); }
    private final MovementTiming timing=new MovementTiming();
    private long generation,graceUntil;
    private boolean grace,initialized;
    private MovementSettings settings=MovementSettings.defaults();
    public void reset(long now,int graceMillis,String reason) {
        dispatcher.reset(reason); timing.reset();
        long deadline=now+graceMillis*1_000_000L;
        if(!grace || deadline-graceUntil>0) graceUntil=deadline;
        grace=true;
    }
    public void process(ConfigSnapshot config,PacketFrame frame,MovementFrame movement,ConnectionSnapshot connection,
                        OwnerObservation owner,ServerTickHealth.Snapshot health,BedrockStatus edition,long session,int entityId) {
        long now=frame.observedNanos();
        if(!initialized || generation!=config.generation()) {
            boolean first=!initialized; initialized=true; generation=config.generation(); settings=config.movement();
            dispatcher.configure(generation,settings);
            reset(now,first?settings.joinGraceMillis():settings.lagGraceMillis(),first?"JOIN_GRACE":"CONFIGURATION_CHANGED");
        }
        PacketKind kind=frame.packet().kind();
        if(kind==PacketKind.TELEPORT || kind==PacketKind.WORLD_RESET) { reset(now,settings.teleportGraceMillis(),"TELEPORT_GRACE"); return; }
        if(frame.packet() instanceof Impulse impulse && (impulse.explosion() || impulse.entityId()==entityId)) {
            reset(now,settings.velocityGraceMillis(),"VELOCITY_GRACE"); return;
        }
        if(kind==PacketKind.WORLD_CHANGE || kind==PacketKind.EFFECT || kind==PacketKind.ABILITIES || kind==PacketKind.VEHICLE) {
            reset(now,settings.lagGraceMillis(),"STATE_CHANGE"); return;
        }
        if(frame.direction()!=PacketDirection.INBOUND || !(frame.packet() instanceof Movement)) return;
        HashSet<String> gates=new HashSet<>();
        if(grace && now-graceUntil<0) gates.add("GRACE");
        if(connection==null || connection.lossEpoch()!=connection.processedEpoch()) gates.add("PACKET_LOSS");
        if(connection!=null) {
            if(connection.processingDelayNanos()>settings.maximumDelayMillis()*1_000_000L) gates.add("WORKER_DELAY");
            if(connection.timing().jitterMillis()>settings.maximumJitterMillis()) gates.add("JITTER");
            if(connection.uncertain()) gates.add("CONNECTION_UNCERTAIN");
        }
        // Snapshot newer than the observed packet cannot establish server health at its arrival.
        if(!health.known() || now-health.observedNanos()<0 || now-health.observedNanos()>settings.maximumDelayMillis()*1_000_000L)
            gates.add("SERVER_HEALTH_UNKNOWN");
        else if(health.intervalNanos()>settings.maximumDelayMillis()*1_000_000L) gates.add("SERVER_LAG");
        var exemptions=config.exemptions();
        Set<String> bypasses=combined(owner==null || !exemptions.enabled() || !exemptions.permissions()?Set.of():owner.bypasses());
        if(owner==null || now-owner.observedNanos()<0 || now-owner.observedNanos()>settings.safeAgeMillis()*1_000_000L)
            gates.add("OWNER_STATE_UNKNOWN");
        else {
            if(exemptions.enabled() && exemptions.creativeOrSpectator() && owner.creativeOrSpectator()) gates.add("GAMEMODE");
            if(exemptions.enabled() && exemptions.flight() && owner.flight()) gates.add("FLIGHT_ALLOWED");
            if(exemptions.enabled() && exemptions.worlds().contains(owner.world())) gates.add("WORLD_EXEMPT");
            if(owner.vehicle()) gates.add("VEHICLE");
            if(owner.gliding()) gates.add("ELYTRA");
        }
        if(edition!=BedrockStatus.JAVA) gates.add("UNKNOWN_EDITION");
        if(!frame.protocol().known()) gates.add("UNSUPPORTED_PROTOCOL");
        // Impaired conditions erase the time bank, preventing old debt from reappearing after recovery.
        boolean impaired=gates.stream().anyMatch(r->!r.equals("UNKNOWN_EDITION"));
        if(impaired) timing.clearDebt();
        if(gates.contains("WORKER_DELAY") || gates.contains("SERVER_LAG") || gates.contains("JITTER") || gates.contains("CONNECTION_UNCERTAIN")) {
            long deadline=now+settings.lagGraceMillis()*1_000_000L;
            if(!grace || deadline-graceUntil>0) graceUntil=deadline;
            grace=true; gates.add("GRACE");
        }
        var t=settings.timing();
        var sample=timing.observe(now,t.windowMillis(),t.nominalTickMillis(),t.bankMillis(),t.silenceMillis(),t.burstMillis(),t.burstPackets());
        dispatcher.timing(frame.sequence(),now,sample,gates,bypasses);
        if(movement==null) { gates.add("NO_MOVEMENT_MODEL"); dispatcher.unavailable(frame.sequence(),now,gates); }
        else dispatcher.movement(movement,session,gates,bypasses);
    }
    public MovementChecksSnapshot snapshot(long now,long revision) { return dispatcher.snapshot(now,revision); }
}
