# Check catalog — development candidate 0.12

**72 catalog IDs: 61 implemented experimental evaluators/diagnostics, 11 unavailable
models. Movement has 15 evaluators and 3 unavailable models. Actions default off.** Every working
entry is experimental, supports only the described subset, and has Bedrock/UNKNOWN
policy `diagnostic` or `disabled`. None is recommended for automatic punishment.
Runtime geometry remains uncertain; diagnostics never increase buffers. See
[PHASE4.md](PHASE4.md) for the dispatcher and prerequisites.

| ID | Mechanism / evidence | Legitimate cases and limits |
| --- | --- | --- |
| SpeedA | Horizontal displacement above all simulated candidates | Ice, speed effects, sprint, impulse, partial blocks; inherited model uncertainty |
| FlyA | Unsupported ascent/hover when every vertical alternative descends | Jumps, slow falling, levitation, flight, liquid, ladders; no universal fixed Y limit |
| TimerA | Sustained movement tick-time surplus across arrival windows | Bunching credit, jitter, delay, loss and grace; constant ping is not itself a bypass |
| BlinkA | Movement silence followed by compressed burst | Ordinary network buffering can match; silence alone never mismatches |
| GroundA | Claimed ground without captured collision support | Partial shapes, world changes, missing chunks and translated geometry |
| NoFallA | Ground claim during accumulated unsupported descent | Actual landing produces no mismatch; no damage cancellation or damage-based inference |
| StepA | Supported rise beyond simulated jump/step alternatives | Slabs, stairs, snow, ceilings; incomplete step models stay uncertain |
| ClimbA | Climb-context ascent beyond nominal candidates | Vines/ladders and contact changes; exact climb mechanics remain uncertain |
| LiquidA | Liquid-context vertical motion outside nominal alternatives | Currents, swimming, bubble columns, enchantments; diagnostic-only current model |
| PhaseA | Swept reported displacement clipped by geometry | Thin blocks, partial shapes, steps, initial overlap, pistons and world changes |
| AccelerationA | Horizontal velocity change exceeds candidate alternatives | Starting/stopping, friction, effects and velocity; correlated with SpeedA |
| GravityA | Vertical displacement outside full candidate interval | Jump boost, levitation, slow falling, floor/ceiling contact and impulses |
| AirJumpA | Unsupported upward reversal when no candidate permits ascent | Grounded jumps and legal upward alternatives are excluded |
| SprintA | Sprint-context horizontal envelope mismatch | Correlated speed signal, not a second independent proof or omni-sprint certification |
| SneakA | Sneak-context horizontal envelope mismatch | Sneak-edge and pose behavior remain uncertain; not a complete sneak emulator |
| NoSlowA | **Unavailable** | No acknowledged active-item type/duration/slowdown context |
| ElytraA | **Unavailable** | No validated glide, firework and transition model |
| VehicleA | **Unavailable** | No vehicle-specific state, dimensions and simulation |

All working entries use configurable per-check buffers, consecutive samples,
time-based decay and cooldown-limited compact evidence. Join/teleport/velocity/lag
and world/session loss gates apply before trusted buffering. Explicit exemptions
suppress diagnostic records too. Per-check disablement never disables its neighbors.
Malformed movement remains uncertainty; implemented protocol/exploit diagnostics are listed below.

NoSlowA/ElytraA/VehicleA have no fake detector classes. Configuration refuses to
enable them. The combat/world/player/inventory/protocol/exploit categories below have implemented
diagnostic rules; their explicitly unavailable models cannot be enabled. Full movement-model
coverage and independent legitimate live traces are still prerequisites for a
production detection claim. Phase 8 separately aggregates only eligible findings; diagnostic evidence never contributes.

## Combat signals

All 15 combat evaluators are experimental. Runtime observations always carry
uncertainty and cannot increase buffers or findings. Every ID has an independent
rule in `checks/combat.yml`. Multiple IDs can share the same input; they are not
independent confidence votes. See [PHASE5.md](PHASE5.md) for data and trust contracts.

| ID | Exact diagnostic signal | Material limit |
| --- | --- | --- |
| ReachA | Minimum eye-to-box distance exceeds owner-sampled range plus tolerance | Nominal player boxes, conservative retained alternatives; no exact latency rewind |
| HitboxA | Reported attack ray misses every retained box/envelope | Pose, client-applied rotation and delivery unconfirmed |
| AimA | Large recent rotation followed by tight target-center alignment | Legitimate snaps can match; no sensitivity model |
| AimAssistA | Sustained low-variation rotation steps with tight target alignment | Statistical correlation, not proof of assistance |
| RotationA | Sustained low-variation nonzero rotation steps during attacks | Quantization and device behavior unmodeled |
| AutoClickerA | Low swing interval variation AND high repeated-interval ratio | Minimum statistics required; CPS alone never triggers |
| MultiAuraA | More than one distinct target inside the target window | Legitimate fast switching can match |
| KillAuraA | Target-ray miss AND fast switching AND missing matched swing | Correlated diagnostics, unconfirmed client execution |
| NoSwingA | No main-hand swing matched before/after attack in the window | Packet observation, not a physical input measurement |
| AttackPatternA | Four rapid targets alternate A-B-A-B | Legitimate target alternation can match |
| AttackTimingA | Repeated low-variation attack intervals AND missing swing | No weapon cooldown/damage acceptance model |
| InvalidAttackA | Attack refers to the viewer's own bound entity ID | Unknown target IDs are not assumed invalid |
| ImpossibleInteractionA | Attack carries a reported pitch outside [-90,90] | Protocol-specific client handling remains unvalidated |
| CriticalsA | Repeated distinct tiny airborne descents at attack time | Critical damage never assumed applied |
| VelocityA | Displacement projected onto local server impulse is too small | Anti-knockback shares this signal; collisions, friction and client application unconfirmed |

Configuration changes, loss, teleport/world/protocol reset and quit discard pending
combat evidence. Exemptions and disabled checks cannot emit diagnostic records.
No combat check cancels attacks or applies punishment.

## Phase 6 world/player/inventory/protocol/exploit catalog

All implemented entries emit diagnostics only. Buffers and findings remain zero;
`limit` and cooldown control diagnostic emission, with shared exemptions and
per-category grace. The descriptions below state the actual predicates, not broader
production detection guarantees. [PHASE6.md](PHASE6.md) details the missing models.

| ID | Category | Status | Signal or missing prerequisite |
| --- | --- | --- | --- |
| BlockReachA | world | EXPERIMENTAL | Eye-to-clicked-block distance exceeds sampled block range |
| DirectionA | world | EXPERIMENTAL | Reported block face lies opposite the attacker eye |
| RotationPlacementA | world | EXPERIMENTAL | Reported placement ray misses clicked block |
| GhostHandA | world | EXPERIMENTAL | Captured solid shape intersects the eye-to-target segment |
| FastPlaceA | world | EXPERIMENTAL | Placement observations exceed the full-window rate |
| NukerA | world | EXPERIMENTAL | Distinct completed dig targets exceed the full-window budget |
| InvalidDigA | world | EXPERIMENTAL | Completed or cancelled dig does not match the observed start |
| LiquidInteractionA | world | EXPERIMENTAL | Dig completion targets captured water or lava |
| ScaffoldA | world | EXPERIMENTAL | Rapid below-feet placements combine with ray mismatch |
| TowerA | world | EXPERIMENTAL | Repeated vertical below-feet placement progression |
| ImpossiblePlaceA | world | EXPERIMENTAL | Placement cursor or face is outside protocol bounds |
| FastBreakA | world | UNAVAILABLE | Requires hardness, tools, enchantments and acknowledged block state |
| ImpossibleBreakA | world | UNAVAILABLE | Requires authoritative break eligibility and server acceptance |
| UseOrderA | player | EXPERIMENTAL | Release-use action without a retained use start |
| FastUseA | player | EXPERIMENTAL | Use request observations exceed the full-window rate |
| FastEatA | player | UNAVAILABLE | Requires confirmed active item and consumption completion |
| FastBowA | player | UNAVAILABLE | Requires bow-specific charge and accepted projectile state |
| RegenA | player | UNAVAILABLE | Requires authoritative health, effects and healing causes |
| AutoRespawnA | player | UNAVAILABLE | Requires authoritative death and respawn lifecycle |
| PortalInventoryA | player | UNAVAILABLE | Requires authoritative portal transition and menu state |
| InventoryMoveA | inventory | EXPERIMENTAL | Reported displacement while a server menu is observed open |
| InventorySprintA | inventory | EXPERIMENTAL | Sprint start while a server menu is observed open |
| InvalidSlotA | inventory | EXPERIMENTAL | Held hotbar slot outside 0 through 8 |
| ImpossibleInventoryA | inventory | EXPERIMENTAL | Click references a different observed server menu |
| SlotSpoofA | inventory | EXPERIMENTAL | Unsupported negative click slot excluding outside-click sentinel |
| BadPacketsA | protocol | EXPERIMENTAL | Nonfinite numeric values in normalized inbound observations |
| InvalidPositionA | protocol | EXPERIMENTAL | Reported position exceeds the protocol coordinate envelope |
| InvalidPitchA | protocol | EXPERIMENTAL | Reported pitch outside minus 90 through 90 degrees |
| InvalidSequenceA | protocol | EXPERIMENTAL | Modern interaction sequence repeats or reverses |
| TransactionA | protocol | EXPERIMENTAL | Unmatched or ambiguous timing acknowledgement |
| InvalidEntityInteractionA | protocol | EXPERIMENTAL | Self interaction or nonfinite target-local vector |
| ImpossibleClientStateA | protocol | EXPERIMENTAL | Reported flight after an observed server flight prohibition |
| PacketOrderA | protocol | EXPERIMENTAL | Position reports while an observed teleport is unconfirmed |
| PacketSpamA | exploit | EXPERIMENTAL | Normalized inbound observations exceed the full-window rate |
| MovementSpamA | exploit | EXPERIMENTAL | Movement observations exceed the full-window rate |
| InteractionSpamA | exploit | EXPERIMENTAL | Entity interaction observations exceed the full-window rate |
| PayloadSpamA | exploit | EXPERIMENTAL | Payload observations exceed the full-window rate |
| PayloadSizeA | exploit | EXPERIMENTAL | Normalized payload bytes exceed the configured diagnostic budget |
| BookExploitA | exploit | UNAVAILABLE | Requires bounded item-component decoding and version-specific limits |

## Phase 7 edition modes

Every ID above has an explicit rule in `profiles/bedrock.yml` and an independent
Java enable switch. Eight normalized sanity predicates default BEDROCK_SUPPORTED,
seven rate/count predicates default BEDROCK_ADJUSTED at sensitivity 0.5, and all
remaining 57 IDs default BEDROCK_DISABLED. The complete default grouping and override
formula are in [BEDROCK.md](BEDROCK.md). UNKNOWN uses that conservative policy too.
Older `bedrock-mode: disabled`, global and category disables remain vetoes.

Mode describes diagnostic reuse, not certified native support. Confirmed Bedrock
never enters the Java physics tracker. Java combat geometry, aim/click statistics,
menu movement/timing and placement geometry default disabled for translated clients.
No new cheat finding or enforcement path is introduced by Phase 7.

## Phase 8 evidence output

All three dispatchers publish an immutable output record only when they emit evidence.
The detector uncertainty gates are unchanged. Guard evidence remains observation-only;
runtime speculative movement/combat evidence remains ineligible for automatic action.
Per-check buffers remain separate from the decaying Phase 8 VL/risk ledger. Staff
verbose alerts and optional logs can expose diagnostics without converting them into
trusted findings. See PHASE8.md for all output/action gates.
