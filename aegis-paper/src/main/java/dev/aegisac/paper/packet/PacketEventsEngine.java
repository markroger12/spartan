package dev.aegisac.paper.packet;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.player.PlayerData;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** External PacketEvents integration. Only immutable normalized values leave callbacks. */
public final class PacketEventsEngine implements PacketEngine {
    private final PacketEventsAPI<?> api;
    private final PacketRouter<User> router;
    private final PacketNormalizer normalizer;
    private final ClientVersionProvider<User> versions;
    private final LongSupplier clock;
    private final AtomicInteger probeIds = new AtomicInteger(ThreadLocalRandom.current().nextInt());
    private volatile boolean running;
    private boolean closed;
    private final PacketListenerAbstract listener = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
        @Override public void onPacketReceive(PacketReceiveEvent event) { observe(event, PacketDirection.INBOUND); }
        @Override public void onPacketSend(PacketSendEvent event) { observe(event, PacketDirection.OUTBOUND); }
    };
    public PacketEventsEngine(PacketEventsAPI<?> api, PacketRouter<User> router) {
        this(api, router, new PacketEventsNormalizer(router.settings().maximumPacketBytes()),
                new PacketEventsVersionProvider(), System::nanoTime);
    }
    public PacketEventsEngine(PacketEventsAPI<?> api, PacketRouter<User> router, PacketNormalizer normalizer,
                              ClientVersionProvider<User> versions, LongSupplier clock) {
        this.api = api; this.router = router; this.normalizer = normalizer; this.versions = versions; this.clock = clock;
    }
    @Override public synchronized void start() {
        if (closed) throw new IllegalStateException("Packet engine is closed");
        if (running) return;
        if (!api.isInitialized() || api.isTerminated()) throw new IllegalStateException("PacketEvents is not initialized");
        api.getEventManager().registerListener(listener); running = true;
    }
    /** Platform lifecycle only; network callbacks never touch Player. */
    public boolean attach(Player player, PlayerData data) {
        User user = api.getPlayerManager().getUser(player);
        if (user == null) return false;
        data.bindEntity(user.getEntityId());
        router.attach(player.getUniqueId(), user, data);
        return true;
    }
    public void detach(UUID uuid) { router.detach(uuid); }
    private void observe(ProtocolPacketEvent event, PacketDirection direction) {
        if (!running || event.isCancelled() || event.getConnectionState() != ConnectionState.PLAY) return;
        User user = event.getUser();
        if (user == null) return;
        UUID uuid = user.getUUID();
        ClientProtocol protocol = versions.protocol(user);
        long now = clock.getAsLong();
        try {
            // Prejoin/late and unsupported-protocol traffic need no wrapper decoding.
            NormalizedPacket packet = router.attached(uuid, user) && protocol.known()
                    ? normalizer.normalize(event) : NormalizedPacket.Other.INSTANCE;
            router.observe(uuid, user, direction, now, protocol, packet);
        } catch (RuntimeException malformed) {
            // Decode failure is uncertainty/telemetry, never proof of cheating. No hostile payload is logged.
            router.invalid(uuid, user);
        }
    }
    /** Optional 1.17+ probes. No legacy window traffic is injected and no incoming packet is cancelled. */
    public void probeConnections() {
        if (!running || !router.settings().activeProbes()) return;
        router.forEachConnection(user -> {
            if (!versions.protocol(user).pingPong()) return;
            try { user.sendPacket(new WrapperPlayServerPing(probeIds.getAndIncrement())); }
            catch (RuntimeException disconnected) { router.invalidate(user.getUUID(), user); }
        });
    }
    @Override public boolean running() { return running; }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (running) { running = false; api.getEventManager().unregisterListener(listener); }
        router.close();
    }
}
