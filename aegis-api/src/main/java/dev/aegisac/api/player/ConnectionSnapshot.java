package dev.aegisac.api.player;
/** Counters and uncertainty are telemetry, never violations. No acknowledgement fence is promised. */
public record ConnectionSnapshot(TimingSnapshot timing, long processedPackets, long sequence,
                                 long inboundGapNanos, double inboundPacketsPerSecond,
                                 long lossEpoch, long processedEpoch, boolean uncertain,
                                 long uncertaintyUntilNanos, long processingDelayNanos) { }
