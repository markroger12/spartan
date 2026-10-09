package dev.aegisac.common.packet;
/** Sequence is local to one session and orders ingress across both directions. */
public record PacketFrame(long sequence, long epoch, long observedNanos, PacketDirection direction,
                          ClientProtocol protocol, NormalizedPacket packet) { }
