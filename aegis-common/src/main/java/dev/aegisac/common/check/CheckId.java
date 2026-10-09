package dev.aegisac.common.check;
/** Stable IDs; unavailable entries are explicitly rejected if enabled, never silent no-ops. */
public enum CheckId {
    SpeedA("Horizontal displacement outside candidate envelope"),
    FlyA("Sustained unsupported ascent or hover"),
    TimerA("Sustained movement packet time surplus"),
    BlinkA("Movement silence followed by a compressed burst"),
    GroundA("Ground claim without collision support"),
    NoFallA("Ground reset during an unsupported fall"),
    StepA("Grounded rise beyond simulated step/jump alternatives"),
    ClimbA("Ascent beyond climb candidate envelope"),
    LiquidA("Vertical liquid movement outside nominal candidates"),
    NoSlowA("Requires acknowledged active-item slowdown", false),
    PhaseA("Reported displacement crosses collision geometry"),
    AccelerationA("Horizontal acceleration beyond candidate alternatives"),
    GravityA("Vertical movement outside simulated alternatives"),
    AirJumpA("Upward reversal without a supported jump"),
    SprintA("Sprint-context horizontal envelope mismatch"),
    SneakA("Sneak-context horizontal envelope mismatch"),
    ElytraA("Requires validated glide/firework model", false),
    VehicleA("Requires vehicle-specific dimensions and physics", false);
    public final String description;
    public final boolean implemented;
    CheckId(String description) { this(description,true); }
    CheckId(String description,boolean implemented) { this.description=description; this.implemented=implemented; }
    public boolean timing() { return this==TimerA || this==BlinkA; }
}
