package dev.aegisac.common.packet;
import dev.aegisac.common.player.PlayerData;
import java.util.function.LongSupplier;
/** Ordered state processor; player-owned movement and combat dispatch follow state transitions. */
public final class PacketProcessor implements PacketListener {
    private final LongSupplier clock;
    public PacketProcessor(LongSupplier clock) { this.clock = clock; }
    @Override public void onPacket(PlayerData player, PacketFrame frame) { player.process(frame, clock.getAsLong()); }
}
