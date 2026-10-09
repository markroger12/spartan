package dev.aegisac.common.physics;
/** Exact protocol baselines only; intermediate/future protocols do not inherit newest physics. */
public enum PhysicsProfile {
    JAVA_1_8(47,false,.005), JAVA_1_16_5(754,true,.003), JAVA_1_21_11(774,true,.003);
    private final int protocol;
    public final boolean modernAxisOrder;
    public final double negligibleMotion;
    PhysicsProfile(int protocol, boolean modern, double negligible) {
        this.protocol=protocol; modernAxisOrder=modern; negligibleMotion=negligible;
    }
    public static PhysicsProfile forProtocol(int id) {
        for (PhysicsProfile p:values()) if(p.protocol==id) return p;
        return null;
    }
}
