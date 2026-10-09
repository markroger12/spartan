# Phase 3: experimental collision and physics

This is the Phase 3 contract. The later check layer and current coverage are in
[PHASE4.md](PHASE4.md).

Phase 3 adds a working collision/simulation layer to AegisAC's packet pipeline.
It still has **zero checks, no verified safe positions and no enforcement**.
This is an experimental, bounded diagnostic model, not a complete or bit-exact
Minecraft client emulator. Synthetic fixtures establish the implemented contracts;
they do not establish production false-positive rates or live-client conformance.

## Ownership and world snapshots

`WorldCaptureService` keeps one entry per attached player and a round-robin queue.
A Bukkit timer captures at most `physics.captures-per-tick` players each server tick.
All location, attribute, effect, block, shape and entity queries occur on that
thread. Explicit ownership checks reject asynchronous calls before reading players.
Folia remains rejected at bootstrap: a primary-thread assertion is not region safety.

`WorldCapture` scans a configured horizontal radius and six vertical block layers,
clipped to world height. The outer block layer is a border for protruding geometry;
only the interior is usable coverage. It checks chunk availability, including
neighbor chunks around shape queries, and never explicitly requests a chunk load.
Cells, retained collision boxes and retained entity boxes have independent hard
limits. Default capture scans at most 150 cells per player and two players per tick.
A limit or unloaded chunk is explicit uncertainty; missing cells are not considered
known air. Bukkit's internal shape/spatial-query implementation may allocate before
returning results; our budget bounds our iteration and retained data, not those
platform internals. No large-server performance claim is made.

Bukkit collision shapes supply local boxes, translated once into absolute world
coordinates. Slabs, stairs, doors, trapdoors, fences, walls, snow and carpets retain
their pieces rather than being approximated as solid cubes. These are **server**
shapes; entity-dependent shapes and translated clients need additional modeling.
Material traits record liquids (including waterlogging), climbables, ice variants,
slime, honey, soul sand, cobwebs, scaffolding, bubble columns, pistons and selected
special blocks. Nearby entity AABBs are retained separately: intersections add
uncertainty because pushing and vehicle collision response are not modeled.

Snapshots contain only immutable core records. `WorldView` publishes by CAS against
a revision token. Invalidation or closure during capture prevents that capture
from being published. There is one retained snapshot per session; no unbounded
chunk cache, world registry or snapshot history accumulates. Closure releases it.
The snapshot is reused by packet workers until replaced, invalidated or expired.

Block changes, physics, growth, fluids, pistons, explosions, entity block edits,
structure growth and chunk load/unload events conservatively invalidate captured
players in that world. Teleports, respawns and world switches reset session state.
Outgoing chunk/block/reset packets invalidate immediately at router ingress and
again in ordered processing. Large chunk bodies are never decoded just to
invalidate a snapshot. Client dig/place observations invalidate their snapshot too.
Silent plugin edits are additionally bounded by TTL and future outgoing traffic;
this is not a guarantee that every conceivable plugin edit emits an event.

## Collision and version contracts

`Aabb` validates finite, ordered bounds. Touching faces are contact, not overlap.
`CollisionSolver` clips a swept box vertically and then horizontally, preventing
endpoint-only tunneling through thin shapes. Legacy profiles use X then Z;
modern profiles prioritize the larger horizontal component. Bounded step attempts
compare two clearance paths and choose greater horizontal progress. Step results
carry `STEP_MODEL`: modern alternate stepping and exact client epsilon behavior
still need a live trace corpus. Initial penetration adds `INITIAL_OVERLAP` rather
than pretending collision resolution has repaired the baseline.

| Explicit model | Protocol | Implemented differences |
| --- | --- | --- |
| Java 1.8 | 47 | Legacy axis order, 0.005 motion cutoff and legacy ground acceleration coefficient |
| Java 1.16.4/1.16.5 | 754 | Modern horizontal order, 0.003 cutoff and modern ground acceleration coefficient |
| Java 1.21.11 | 774 | Explicit current baseline using the modern ordinary-motion subset |

The official PacketEvents enums are tested against these IDs. Other protocols,
including known intermediate or future versions, are `UNSUPPORTED_VERSION`.
The server adapter's geometry baseline is 1.21.11; other Bukkit version strings
are marked unsupported. Older client models used against this server's shapes
always add `TRANSLATED_GEOMETRY`. Protocol identification does not promise legacy
block-state translation. Java models are never assumed appropriate for unknown
Bedrock identity; runtime results also carry `UNKNOWN_EDITION` until identity exists.

## Simulation mechanics

`PhysicsEngine.step` accepts an explicit initial state, input, profile and immutable
world snapshot. It computes one tick and returns displacement, next velocity,
contact/step information and uncertainty. It has no access to Bukkit, packets,
configuration mutation, check scores or enforcement.

| Mechanics | Current behavior |
| --- | --- |
| Walk, sprint, air motion, gravity, friction, jump | Deterministic one-tick formulas; nine directional inputs, sprint impulse and bounded step attempts |
| Speed/slowness and plugin speed modifiers | Captured effective movement attribute, with server sprint multiplier removed before candidate sprint input |
| Jump boost, levitation, slow falling | Explicit captured effect parameters; unavailable legacy effects add uncertainty |
| Ice, packed/frosted ice, blue ice | Separate friction coefficients; legacy blue ice is translated/uncertain |
| Slime | Illustrative landing bounce and friction; `SLIME` because contact details are incomplete |
| Water/lava | Nominal acceleration, damping and gravity; always `FLUID_FLOW` because currents, fluid height, swimming and enchantments are incomplete |
| Ladders/vines | Nominal horizontal/fall clamps and upward motion; always `CLIMBABLE` |
| Cobweb, honey, soul sand | Illustrative damping/clamping; explicit per-mechanic uncertainty, including enchantment/contact ambiguity |
| Pistons, bubble columns, scaffolding, special blocks | Explicit uncertainty; no claimed full motion model |
| Elytra/fireworks, vehicles, riptide, flight, non-standing pose | Captured uncertainty; ordinary-motion output cannot establish an allowed movement envelope |
| Nearby entities, unsupported effects/attributes | Explicit uncertainty; no guessed push or missing attribute behavior |
| Explicit applied velocity/explosion fixture | Initial velocity vectors are simulated and collided normally |
| Runtime server velocity/explosion packets | Reset baseline with `VELOCITY_TIMING`; no assumption about when the client applied the impulse |
| Teleport/world change, queue loss or malformed coordinates | Reset/invalidate; no comparison against stale state |

Constants express the experimental version model, not administrator punishment
thresholds. Double arithmetic and standard trig are used; exact client float/math
tables, full modern movement input transformations and all patch-specific mechanics
are not emulated. Do not claim tight movement detection from these formulas.

## Runtime packet analysis

`PhysicsTracker` consumes ordered normalized movement packets. Given two suitable
position reports, fresh geometry and an explicit model, it tries at most **18**
inputs (nine directions, jump/no-jump), using observed sprint/sneak state. The
closest candidate supplies the next provisional velocity. The next baseline is
reanchored to the reported position, not stored as a verified safe position.

Position-suppressed reports reset the model because their elapsed client-tick count
is unknown. Intervals outside 25–100 ms also reset candidate generation; this is
an analysis precondition, **not a timer check**. Burst, stale, future-dated, missing
or unsupported inputs produce no comparison. Sweeps outside coverage remain
uncertain instead of scanning more world from the packet thread.

All runtime comparisons carry `INPUT_INFERRED` and `UNACKNOWLEDGED_WORLD`.
Server capture time is not client receipt time. Phase 2 timing has no acknowledged
world-update fence, and Phase 3 does not pretend to add one. The single latest
snapshot can be newer than a queued packet; that packet gets `FUTURE_WORLD` and
zero candidates. Snapshot age uses observation time, while API reads additionally
hide expired results. Ingress loss and revision changes hide old diagnostics.

`/ac physics <player>` (`aegisac.physics`, operator/admin default) reports model,
candidate count, residual and uncertainty. Residual is a distance in blocks;
**it is not a violation or a universal threshold**. -1 means unavailable.
`/ac performance` adds successful capture and capture-failure counters. Unexpected
capture exceptions invalidate the view and log once per enable to avoid spam.

The read-only API adds immutable `PhysicsSnapshot`. Integrations must rebuild
against 0.3.0 because a pre-release record constructor changed. Settings are in
`performance.yml: physics`; limits and enablement require restart. Messages remain
reloadable. Existing version-1 administrator files inherit defaults without rewrite.

## Validation and next gate

Tests cover hand-calculated one-tick motion, a complete synthetic jump arc, sprint,
diagonal normalization, version cutoffs/order, effects, friction, impulses,
clipping/stepping, special-mechanic uncertainty, owner-thread checks, negative
coordinates, real API protocol IDs, bounded capture, unloaded chunks, shape pieces,
round-robin fairness, failed capture, CAS invalidation/closure, stale/future geometry,
packet bursts, teleports, disconnects, permissions and configuration preservation.
They use synthetic geometry and Mockito/MockBukkit, not captured real-client traces.
Current exact results and artifact checksum are in [VALIDATION.md](VALIDATION.md).

No live Paper/Spigot/Purpur, proxy translation, Folia, large-server benchmark or
real-client replay was run for this phase. Phase 4 must add checks conservatively;
before using prediction residuals for enforcement it must supply acknowledged
client world/input/impulse history, correct supported mechanics and legitimate
live trace regressions. Unsupported states must remain explicitly uncertain.
