package dev.aegisac.common.config;
/** Restart-bound resource budgets. Sensitivity/physics do not belong in these settings. */
public record PipelineSettings(int workers, int executorCapacity, int sessionCapacity, int batchSize,
        int maximumPacketBytes, int pendingTransactions, int historyCapacity,
        long transactionTimeoutMillis, long stallMillis, long uncertaintyMillis,
        double smoothing, boolean activeProbes, int probeIntervalTicks) { }
