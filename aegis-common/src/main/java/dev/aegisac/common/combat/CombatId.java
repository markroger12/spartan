package dev.aegisac.common.combat;
/** All entries are experimental, correlated observations; none authorizes enforcement. */
public enum CombatId {
    ReachA("Minimum eye-to-target distance across retained interpolation alternatives"),
    HitboxA("Attack ray misses all retained target alternatives"),
    AimA("Fast target-aligned snap after a large rotation change"),
    AimAssistA("Sustained low-variation rotation steps aligned with a tracked target"),
    InvalidAttackA("Attack names the viewer entity itself"),
    AttackTimingA("Repeated attack intervals combined with missing swing"),
    ImpossibleInteractionA("Attack has an out-of-range client pitch"),
    RotationA("Repeated low-variation rotation steps while attacking"),
    AutoClickerA("Low interval variation combined with repeated swing intervals"),
    MultiAuraA("Multiple targets attacked inside a short observation window"),
    KillAuraA("Combined target miss, fast switching and missing swing"),
    NoSwingA("No matching main-hand swing before or after an attack"),
    AttackPatternA("Rapid alternating target attack sequence"),
    CriticalsA("Repeated tiny airborne attack descents; critical damage is unconfirmed"),
    VelocityA("Small observed displacement projected onto a server impulse");
    public final String description;
    CombatId(String description) { this.description=description; }
}
