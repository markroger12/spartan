package dev.aegisac.paper.packet;
/** Analysis cannot safely read this bounded packet. This is not an enforcement signal. */
public final class PacketDecodeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PacketDecodeException(String message) { super(message); }
}
