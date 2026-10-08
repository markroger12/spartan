package dev.aegisac.common.player;
import dev.aegisac.api.player.*;
import dev.aegisac.common.config.PipelineSettings;
import dev.aegisac.common.connection.ConnectionTracker;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Worker-owned state with short coherent snapshot reads; ingress marks loss without taking this lock. */
public final class PlayerData {
    private final UUID uuid;
    private final String name;
    private final long sessionId, joinedAt;
    private final dev.aegisac.common.world.WorldView world = new dev.aegisac.common.world.WorldView();
    private final dev.aegisac.common.physics.PhysicsTracker physics = new dev.aegisac.common.physics.PhysicsTracker();
    private dev.aegisac.common.config.PhysicsSettings physicsSettings = dev.aegisac.common.config.PhysicsSettings.DEFAULT;
    public dev.aegisac.common.world.WorldView world() { return world; }
    public synchronized void configurePhysics(dev.aegisac.common.config.PhysicsSettings settings) { physicsSettings = settings; }
    private dev.aegisac.common.check.MovementMonitor checks = new dev.aegisac.common.check.MovementMonitor();
    private final java.util.concurrent.atomic.AtomicReference<dev.aegisac.common.check.OwnerObservation> owner = new java.util.concurrent.atomic.AtomicReference<>();
    private java.util.function.Supplier<dev.aegisac.common.config.ConfigSnapshot> configuration;
    private java.util.function.Supplier<dev.aegisac.common.check.ServerTickHealth.Snapshot> health = dev.aegisac.common.check.ServerTickHealth.Snapshot::unknown;
    private dev.aegisac.common.combat.CombatMonitor combat=new dev.aegisac.common.combat.CombatMonitor();
    private final java.util.concurrent.atomic.AtomicReference<dev.aegisac.common.combat.CombatOwnerObservation> combatOwner=new java.util.concurrent.atomic.AtomicReference<>();
    public void combatOwnerObservation(dev.aegisac.common.combat.CombatOwnerObservation value) { combatOwner.set(value); }
    private dev.aegisac.common.guard.GuardMonitor guard=new dev.aegisac.common.guard.GuardMonitor();
    private final java.util.concurrent.atomic.AtomicReference<dev.aegisac.common.guard.GuardOwnerObservation> guardOwner=new java.util.concurrent.atomic.AtomicReference<>();
    public void guardOwnerObservation(dev.aegisac.common.guard.GuardOwnerObservation value) { guardOwner.set(value); }
    private final java.util.concurrent.atomic.AtomicReference<dev.aegisac.common.bedrock.IdentityObservation> identity=new java.util.concurrent.atomic.AtomicReference<>();
    private java.util.function.LongSupplier identityEpoch=()->0;
    private final dev.aegisac.common.bedrock.BedrockMonitor bedrockMonitor=new dev.aegisac.common.bedrock.BedrockMonitor();
    private dev.aegisac.common.config.ConfigSnapshot effectiveConfiguration;
    private String appliedIdentity="";
    private long appliedProviderEpoch=Long.MIN_VALUE;
    public synchronized void configureIdentity(java.util.function.LongSupplier epoch) { if(!closed) identityEpoch=epoch; }
    public synchronized void identityObservation(dev.aegisac.common.bedrock.IdentityObservation value) { if(!closed) identity.set(value); }
    public synchronized EditionSnapshot editionSnapshot(long now) { return editionView(now); }
    private EditionSnapshot editionView(long now) {
        if(closed) return EditionSnapshot.unknown("CLOSED");
        var value=identity.get();
        if(configuration==null||value==null) return EditionSnapshot.unknown("PROVIDER_UNAVAILABLE");
        var config=configuration.get();
        if(value.generation()!=config.generation()) return EditionSnapshot.unknown("CONFIGURATION_CHANGED");
        if(value.providerEpoch()!=identityEpoch.getAsLong()) return EditionSnapshot.unknown("PROVIDER_CHANGED");
        long age=now-value.identity().observedNanos();
        if(age<0||age>config.edition().identity().maximumAgeMillis()*1_000_000L) return EditionSnapshot.unknown(age<0?"FUTURE_IDENTITY":"STALE_IDENTITY");
        return value.identity();
    }
    private boolean analysisCurrent(long now) {
        return !closed && configuration!=null && effectiveConfiguration!=null && processedEpoch==lossEpoch.get()
                && appliedProviderEpoch==identityEpoch.getAsLong() && effectiveConfiguration.generation()==configuration.get().generation() && appliedIdentity.equals(editionView(now).analysisKey());
    }
    private java.util.function.Consumer<dev.aegisac.common.output.DetectionEnvelope> output;
    public synchronized void configureOutput(java.util.function.Consumer<dev.aegisac.common.output.DetectionEnvelope> sink) { if(!closed) { output=sink; wireOutput(); } }
    private void wireOutput() { checks.output(this::emit); combat.output(this::emit); guard.output(this::emit); }
    private void emit(dev.aegisac.common.output.Detection detection) {
        if(output==null||closed) return;
        var position=state.movement(); var membership=owner.get(); var timing=connection.snapshot(detection.time(),lossEpoch.get(),processedEpoch).timing();
        output.accept(new dev.aegisac.common.output.DetectionEnvelope(uuid,name,sessionId,processedEpoch,appliedProviderEpoch,appliedIdentity,detection,
                System.currentTimeMillis(),client.release(),client.brand(),client.bedrock(),timing.transactionRttMillis(),timing.jitterMillis(),health.get().tps(),
                membership==null?"unknown":membership.world(),position.positionKnown(),position.x(),position.y(),position.z()));
    }
    public synchronized boolean currentOutput(long session,long epoch,long providerEpoch,long generation,String identityKey,long now) {
        return sessionId==session&&!closed&&epoch==lossEpoch.get()&&processedEpoch==epoch&&providerEpoch==identityEpoch.getAsLong()&&configuration!=null&&configuration.get().generation()==generation
                &&identityKey.equals(editionView(now).analysisKey());
    }
    private final Map<String,Long> temporaryExemptions=new HashMap<>();
    private long frozenUntil; private boolean frozen;
    public synchronized void exempt(String check,long now,long durationNanos) {
        if(closed) throw new IllegalStateException("Closed session");
        if(!check.equals("*")) check=dev.aegisac.common.config.CheckCatalog.find(check).id();
        if(durationNanos<=0||durationNanos>3_600_000_000_000L) throw new IllegalArgumentException("Duration must be at most one hour");
        temporaryExemptions.put(check,now+durationNanos);
        // Invalidate already queued evidence and previous buffers before the new exemption is observed.
        markGap();
    }
    public synchronized void frozenUntil(long deadline,boolean enabled) { frozenUntil=deadline; frozen=enabled; markGap(); }
    public synchronized boolean temporarilyExempt(String check,long now) {
        return frozen&&now-frozenUntil<0||temporaryExemptions.containsKey("*")&&now-temporaryExemptions.get("*")<0
                ||temporaryExemptions.containsKey(check)&&now-temporaryExemptions.get(check)<0;
    }
    private Set<String> manualBypasses(long now) {
        if(temporaryExemptions.isEmpty()&&!frozen) return Set.of();
        boolean expired=temporaryExemptions.entrySet().removeIf(e->now-e.getValue()>=0);
        if(expired) { checks=new dev.aegisac.common.check.MovementMonitor(); combat=new dev.aegisac.common.combat.CombatMonitor(); guard=new dev.aegisac.common.guard.GuardMonitor(); wireOutput(); }
        var result=new HashSet<>(temporaryExemptions.keySet()); if(frozen&&now-frozenUntil<0) result.add("*"); return Set.copyOf(result);
    }
    private int entityId=-1;
    public synchronized void configureChecks(java.util.function.Supplier<dev.aegisac.common.config.ConfigSnapshot> configuration,
            java.util.function.Supplier<dev.aegisac.common.check.ServerTickHealth.Snapshot> health) {
        if(closed) throw new IllegalStateException("Closed session");
        this.configuration=configuration; this.health=health;
    }
    public void ownerObservation(dev.aegisac.common.check.OwnerObservation value) { owner.set(value); }
    private final AtomicLong lossEpoch = new AtomicLong();
    private ClientIdentity client = ClientIdentity.unknown();
    private final PacketState state = new PacketState();
    private ConnectionTracker connection;
    private PacketFrame[] history;
    private int historyIndex, historySize;
    private long processedEpoch, lastSequence, inbound, outbound, lastInbound, lastOutbound;
    private boolean closed;
    PlayerData(UUID uuid, String name, long sessionId, long joinedAt) {
        this.uuid = uuid; this.name = name; this.sessionId = sessionId; this.joinedAt = joinedAt;
    }
    public synchronized void configure(PipelineSettings settings) {
        if (connection != null || closed) throw new IllegalStateException("Session already bound or closed");
        connection = new ConnectionTracker(settings); history = new PacketFrame[settings.historyCapacity()];
    }
    public void markGap() { lossEpoch.incrementAndGet(); world.invalidate(); }
    public synchronized void bindEntity(int entityId) { this.entityId=entityId; state.bindEntity(entityId); }
    public long lossEpoch() { return lossEpoch.get(); }
    public synchronized void process(PacketFrame frame, long now) {
        if (closed) return;
        if (connection == null) throw new IllegalStateException("Session must be configured before processing");
        if (frame.sequence() <= lastSequence) throw new IllegalStateException("Out-of-order session processing");
        var global=configuration==null?null:configuration.get();
        long providerEpoch=identityEpoch.getAsLong(); // Fence this analysis even if a provider changes while it runs.
        var edition=editionView(frame.observedNanos());
        if(global!=null && (effectiveConfiguration==null||effectiveConfiguration.generation()!=global.generation()||appliedProviderEpoch!=providerEpoch||!appliedIdentity.equals(edition.analysisKey()))) {
            boolean editionChanged=effectiveConfiguration!=null&&(appliedProviderEpoch!=providerEpoch||!appliedIdentity.equals(edition.analysisKey()));
            effectiveConfiguration=dev.aegisac.common.bedrock.EditionPolicy.apply(global,edition.status()); appliedIdentity=edition.analysisKey(); appliedProviderEpoch=providerEpoch;
            checks=new dev.aegisac.common.check.MovementMonitor(); combat=new dev.aegisac.common.combat.CombatMonitor(); guard=new dev.aegisac.common.guard.GuardMonitor();
            wireOutput();
            bedrockMonitor.clear();
            if(editionChanged) { state.reset(); physics.reset(dev.aegisac.common.physics.Uncertainty.UNKNOWN_EDITION); world.invalidate(); connection.uncertain(frame.observedNanos()); }
        }
        var manual=manualBypasses(now); checks.manualBypasses(manual); combat.manualBypasses(manual); guard.manualBypasses(manual);
        var config=effectiveConfiguration;
        client=new ClientIdentity(client.protocolVersion(),client.release(),edition.status(),client.brand());
        lastSequence = frame.sequence();
        if (frame.epoch() != processedEpoch || frame.packet().kind() == PacketKind.WORLD_RESET
                || (inbound + outbound > 0 && client.protocolVersion() != frame.protocol().id())) {
            if(config!=null) checks.reset(frame.observedNanos(),config.movement().lagGraceMillis(),"PACKET_LOSS");
            combat.reset(frame.observedNanos(),config==null?0:config.combat().graceMillis(),"PACKET_LOSS");
            bedrockMonitor.clear();
            combatOwner.set(null); guardOwner.set(null); guard.reset(frame.observedNanos(),"PACKET_LOSS");
            physics.reset(dev.aegisac.common.physics.Uncertainty.PACKET_GAP); world.invalidate();
            processedEpoch = frame.epoch(); connection.reset(frame.observedNanos()); state.reset();
            Arrays.fill(history, null); historyIndex = historySize = 0;
        }
        var guardWorld=world.read();
        if (frame.packet().kind() == PacketKind.WORLD_CHANGE || frame.packet().kind() == PacketKind.TELEPORT
                || frame.packet().kind() == PacketKind.PLACEMENT || frame.packet().kind() == PacketKind.DIGGING) world.invalidate();
        observe(frame.direction(), frame.observedNanos(), frame.protocol().id(), frame.protocol().release());
        var acknowledgement = connection.observe(frame, now);
        if (!frame.protocol().known()) connection.uncertain(frame.observedNanos());
        if (frame.packet() instanceof Payload payload && !payload.brand().isEmpty())
            client = new ClientIdentity(client.protocolVersion(), client.release(), client.bedrock(), payload.brand());
        if (frame.packet() instanceof Teleport value) {
            connection.uncertain(frame.observedNanos());
            if (frame.protocol().teleportConfirmation())
                connection.timing(new Timing(TimingKind.TELEPORT, value.id(), 0, true), PacketDirection.OUTBOUND, frame.observedNanos());
        }
        if(config!=null) guard.process(config,frame,state.movement(),guardWorld,guardOwner.get(),owner.get(),
                connection.snapshot(now,lossEpoch.get(),processedEpoch),health.get(),client.bedrock(),acknowledgement,entityId);
        if (!state.apply(frame, acknowledgement)) connection.uncertain(frame.observedNanos());
        if (physicsSettings.enabled()) physics.process(frame, world.read(), state.activity(), client.bedrock(),
                physicsSettings.maximumAgeMillis() * 1_000_000L);
        if(config!=null) checks.process(config,frame,physics.movementFrame(),
                connection.snapshot(now,lossEpoch.get(),processedEpoch),owner.get(),health.get(),client.bedrock(),sessionId,entityId);
        if(config!=null) combat.process(config,frame,state.movement(),acknowledgement,
                connection.snapshot(now,lossEpoch.get(),processedEpoch),owner.get(),combatOwner.get(),health.get(),client.bedrock(),entityId);
        if(global!=null) bedrockMonitor.process(global.generation(),global.edition(),edition,frame);
        history[historyIndex] = frame; historyIndex = (historyIndex + 1) % history.length;
        historySize = Math.min(history.length, historySize + 1);
    }
    public synchronized boolean observe(PacketDirection direction, long now, int protocol, String release) {
        if (closed) return false;
        if (client.protocolVersion() != protocol) client = new ClientIdentity(protocol, release, client.bedrock());
        if (direction == PacketDirection.INBOUND) { inbound++; lastInbound = now; }
        else { outbound++; lastOutbound = now; }
        return true;
    }
    public synchronized List<PacketFrame> recentPackets() {
        if (history == null) return List.of();
        List<PacketFrame> result = new ArrayList<>(historySize);
        for (int i = 0; i < historySize; i++) result.add(history[(historyIndex - historySize + i + history.length) % history.length]);
        return List.copyOf(result);
    }
    public synchronized PlayerSnapshot snapshot() { return snapshot(System.nanoTime()); }
    public synchronized PlayerSnapshot snapshot(long now) {
        return new PlayerSnapshot(uuid, name, sessionId, joinedAt, new ClientIdentity(client.protocolVersion(),client.release(),editionView(now).status(),client.brand()), inbound, outbound, lastInbound, lastOutbound,
                connection == null ? null : connection.snapshot(now, lossEpoch.get(), processedEpoch), state.movement(), state.activity(), physicsView(now), checksView(now), combatView(now), guardView(now), editionView(now), analysisCurrent(now)?bedrockMonitor.snapshot(now,configuration.get().edition(),editionView(now)):BedrockSnapshot.empty("ANALYSIS_INVALIDATED"));
    }
    private dev.aegisac.api.check.GuardSnapshot guardView(long now) {
        var value=guard.snapshot();
        if(!analysisCurrent(now) || value.generation()!=configuration.get().generation())
            return dev.aegisac.api.check.GuardSnapshot.empty();
        return value;
    }
    private dev.aegisac.api.check.CombatSnapshot combatView(long now) {
        var value=combat.snapshot(now);
        if(!analysisCurrent(now) || value.generation()!=configuration.get().generation())
            return dev.aegisac.api.check.CombatSnapshot.empty();
        return value;
    }
    private dev.aegisac.api.check.MovementChecksSnapshot checksView(long now) {
        if(configuration==null) return dev.aegisac.api.check.MovementChecksSnapshot.empty();
        var value=checks.snapshot(now,world.read().revision());
        String unavailable=closed?"CLOSED":processedEpoch!=lossEpoch.get()?"PACKET_LOSS":
                value.generation()!=configuration.get().generation()?"CONFIGURATION_CHANGED":!analysisCurrent(now)?"EDITION_OR_SESSION_CHANGED":null;
        if(unavailable==null) return value;
        var hidden=new java.util.LinkedHashMap<String,dev.aegisac.api.check.CheckSnapshot>();
        value.checks().forEach((id,state)->hidden.put(id,new dev.aegisac.api.check.CheckSnapshot(id,"SUPPRESSED",0,0,0,0,Set.of(unavailable))));
        return new dev.aegisac.api.check.MovementChecksSnapshot(value.generation(),hidden,List.of(),null);
    }
    private PhysicsSnapshot physicsView(long now) {
        if(effectiveConfiguration!=null&&!analysisCurrent(now)) return PhysicsSnapshot.unavailable("EDITION_OR_SESSION_CHANGED");
        if(editionView(now).status()==BedrockStatus.BEDROCK) return PhysicsSnapshot.unavailable("BEDROCK_PHYSICS_UNAVAILABLE");
        if (!physicsSettings.enabled()) return PhysicsSnapshot.unavailable("DISABLED");
        if (processedEpoch != lossEpoch.get()) return PhysicsSnapshot.unavailable("PACKET_GAP");
        var value=physics.snapshot();
        var view=world.read();
        if (value.worldRevision()>=0 && value.worldRevision()!=view.revision())
            return PhysicsSnapshot.unavailable("INVALIDATED_WORLD");
        var capture=view.snapshot();
        if (capture!=null && now-capture.capturedNanos()>physicsSettings.maximumAgeMillis()*1_000_000L)
            return PhysicsSnapshot.unavailable("STALE_WORLD");
        return value;
    }
    synchronized void close() {
        closed = true; temporaryExemptions.clear(); frozen=false; output=null; markGap(); world.close(); physics.reset(dev.aegisac.common.physics.Uncertainty.BASELINE_MISSING);
        if (history != null) Arrays.fill(history, null);
        identity.set(null); effectiveConfiguration=null; bedrockMonitor.clear();
        historySize = 0; guardOwner.set(null); guard.reset(System.nanoTime(),"CLOSED"); combatOwner.set(null); combat.reset(System.nanoTime(),0,"CLOSED"); owner.set(null); checks.reset(System.nanoTime(),0,"CLOSED");
        if (connection != null) connection.reset(System.nanoTime());
        state.reset();
    }
}
