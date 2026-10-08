package dev.aegisac.common.guard;
/** Phase 6 catalog. Unavailable models cannot be enabled by configuration. */
public enum GuardId {
    BlockReachA("world",true,"Eye-to-clicked-block distance exceeds sampled block range",0.1),
    DirectionA("world",true,"Reported block face lies opposite the attacker eye",0),
    RotationPlacementA("world",true,"Reported placement ray misses clicked block",0),
    GhostHandA("world",true,"Captured solid shape intersects the eye-to-target segment",0),
    FastPlaceA("world",true,"Placement observations exceed the full-window rate",30),
    NukerA("world",true,"Distinct completed dig targets exceed the full-window budget",12),
    InvalidDigA("world",true,"Completed or cancelled dig does not match the observed start",0),
    LiquidInteractionA("world",true,"Dig completion targets captured water or lava",0),
    ScaffoldA("world",true,"Rapid below-feet placements combine with ray mismatch",0),
    TowerA("world",true,"Repeated vertical below-feet placement progression",0),
    ImpossiblePlaceA("world",true,"Placement cursor or face is outside protocol bounds",0),
    FastBreakA("world",false,"Requires hardness, tools, enchantments and acknowledged block state",0),
    ImpossibleBreakA("world",false,"Requires authoritative break eligibility and server acceptance",0),
    UseOrderA("player",true,"Release-use action without a retained use start",0),
    FastUseA("player",true,"Use request observations exceed the full-window rate",30),
    FastEatA("player",false,"Requires confirmed active item and consumption completion",0),
    FastBowA("player",false,"Requires bow-specific charge and accepted projectile state",0),
    RegenA("player",false,"Requires authoritative health, effects and healing causes",0),
    AutoRespawnA("player",false,"Requires authoritative death and respawn lifecycle",0),
    PortalInventoryA("player",false,"Requires authoritative portal transition and menu state",0),
    InventoryMoveA("inventory",true,"Reported displacement while a server menu is observed open",0.05),
    InventorySprintA("inventory",true,"Sprint start while a server menu is observed open",0),
    InvalidSlotA("inventory",true,"Held hotbar slot outside 0 through 8",0),
    ImpossibleInventoryA("inventory",true,"Click references a different observed server menu",0),
    SlotSpoofA("inventory",true,"Unsupported negative click slot excluding outside-click sentinel",0),
    BadPacketsA("protocol",true,"Nonfinite numeric values in normalized inbound observations",0),
    InvalidPositionA("protocol",true,"Reported position exceeds the protocol coordinate envelope",0),
    InvalidPitchA("protocol",true,"Reported pitch outside minus 90 through 90 degrees",0),
    InvalidSequenceA("protocol",true,"Modern interaction sequence repeats or reverses",0),
    TransactionA("protocol",true,"Unmatched or ambiguous timing acknowledgement",0),
    InvalidEntityInteractionA("protocol",true,"Self interaction or nonfinite target-local vector",0),
    ImpossibleClientStateA("protocol",true,"Reported flight after an observed server flight prohibition",0),
    PacketOrderA("protocol",true,"Position reports while an observed teleport is unconfirmed",0),
    PacketSpamA("exploit",true,"Normalized inbound observations exceed the full-window rate",300),
    MovementSpamA("exploit",true,"Movement observations exceed the full-window rate",40),
    InteractionSpamA("exploit",true,"Entity interaction observations exceed the full-window rate",80),
    PayloadSpamA("exploit",true,"Payload observations exceed the full-window rate",20),
    PayloadSizeA("exploit",true,"Normalized payload bytes exceed the configured diagnostic budget",4096),
    BookExploitA("exploit",false,"Requires bounded item-component decoding and version-specific limits",0);
    public final String category,description;
    public final boolean implemented;
    public final double defaultLimit;
    GuardId(String category,boolean implemented,String description,double defaultLimit) {
        this.category=category; this.implemented=implemented; this.description=description; this.defaultLimit=defaultLimit;
    }
}
