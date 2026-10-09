package dev.aegisac.common.player;
import dev.aegisac.api.player.PlayerSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
/** Membership is owned by platform join/quit events. Packet callbacks only use find(). */
public final class PlayerRegistry implements AutoCloseable {
    private final ConcurrentHashMap<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final AtomicLong sessions = new AtomicLong();
    private boolean closed;
    public synchronized PlayerData join(UUID uuid, String name, long now) {
        if (closed) throw new IllegalStateException("Registry is closed");
        Objects.requireNonNull(uuid); Objects.requireNonNull(name);
        PlayerData data = new PlayerData(uuid, name, sessions.incrementAndGet(), now);
        PlayerData previous = players.put(uuid, data);
        if (previous != null) previous.close();
        return data;
    }
    public PlayerData find(UUID uuid) { return players.get(uuid); }
    public Optional<PlayerSnapshot> snapshot(UUID uuid) {
        PlayerData player = players.get(uuid);
        return player == null ? Optional.empty() : Optional.of(player.snapshot());
    }
    public synchronized void quit(UUID uuid) {
        PlayerData removed = players.remove(uuid);
        if (removed != null) removed.close();
    }
    public int size() { return players.size(); }
    @Override public synchronized void close() {
        closed = true;
        players.values().forEach(PlayerData::close);
        players.clear();
    }
}
