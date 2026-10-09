package dev.aegisac.api.player;
/** RTT is measured at packet observation, not physical wire transmission. -1 means unavailable. */
public record TimingSnapshot(double transactionRttMillis, double keepAliveRttMillis, double jitterMillis,
                             long samples, int pending, long matched, long unknown, long duplicates,
                             long outOfOrder, long expired, long evicted) { }
