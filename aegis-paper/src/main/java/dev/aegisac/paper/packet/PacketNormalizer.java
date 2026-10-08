package dev.aegisac.paper.packet;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import dev.aegisac.common.packet.NormalizedPacket;
/** Runs on the event callback before its buffer is released; return only immutable scalar data. */
@FunctionalInterface
public interface PacketNormalizer { NormalizedPacket normalize(ProtocolPacketEvent event); }
