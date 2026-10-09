package dev.aegisac.common.config;
/** Capture/analysis resource limits, immutable for the lifetime of the plugin. */
public record PhysicsSettings(boolean enabled, int capturesPerTick, int radius, int maximumBlocks,
                              int maximumBoxes, int maximumEntities, int maximumAgeMillis) {
    public PhysicsSettings {
        if(capturesPerTick<1 || capturesPerTick>16 || radius<1 || radius>4
                || maximumBlocks<32 || maximumBlocks>4096 || maximumBoxes<32 || maximumBoxes>4096
                || maximumEntities<1 || maximumEntities>64 || maximumAgeMillis<50 || maximumAgeMillis>2000)
            throw new IllegalArgumentException("Invalid physics resource budget");
    }
    public static final PhysicsSettings DEFAULT = new PhysicsSettings(true,2,2,512,512,16,250);
}
