package dev.aegisac.common.packet;
/** Cache immutable capability mappings at the platform boundary. */
@FunctionalInterface
public interface ClientVersionProvider<C> { ClientProtocol protocol(C connection); }
