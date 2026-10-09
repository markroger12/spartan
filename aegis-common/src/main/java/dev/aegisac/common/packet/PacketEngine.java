package dev.aegisac.common.packet;
/** Owns only this plugin's listener registration, never the shared packet library. */
public interface PacketEngine extends AutoCloseable {
    void start();
    boolean running();
    @Override void close();
}
