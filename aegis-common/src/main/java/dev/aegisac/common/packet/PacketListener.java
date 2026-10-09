package dev.aegisac.common.packet;
import dev.aegisac.common.player.PlayerData;
/** Called serially per session on a packet worker. Implementations must not access Bukkit. */
@FunctionalInterface
public interface PacketListener { void onPacket(PlayerData player, PacketFrame frame); }
