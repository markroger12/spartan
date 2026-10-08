# Developer API — Phase 10 ownership milestone

Compile against `aegis-api` without shading it into your integration plugin, and
declare `depend: [AegisAC]` (or use softdepend plus explicit absence handling).
The platform plugin registers `dev.aegisac.api.AntiCheatApi` with Bukkit's
`ServicesManager` at enable and unregisters at disable.

```java
AntiCheatApi api = Bukkit.getServicesManager().load(AntiCheatApi.class);
if (api != null) {
    api.player(uuid).ifPresent(snapshot -> {
        long inbound = snapshot.inboundPackets();
        ClientIdentity client = snapshot.client();
        // Consume the immutable observation in your integration.
    });
}
```

Resolve Bukkit services on a permitted platform thread. Once acquired, the
read-only API methods are thread-safe:

- `player(UUID)` returns an optional immutable `PlayerSnapshot`.
- `onlinePlayers()` returns the active registry size.
- `configurationGeneration()` identifies the last successfully published snapshot.

Snapshots include UUID, username, session ID, join time, client identity,
inbound/outbound counts and the latest packet times. Times use `System.nanoTime`:
only differences within the same running JVM are meaningful. Zero packet timestamps
mean no observation yet; protocol -1 and release `unknown` mean undetermined.
Client brand is an untrusted informational string, initially `unknown`.
`BedrockStatus.UNKNOWN` must never be treated as confirmed Java. Snapshot values
are coherent per player; there is no cross-player atomic snapshot promise.

Join/quit events own session membership. Packet callbacks never create sessions,
and exact transport binding stops late packets from feeding a reconnect. Plugin
disable closes and removes all sessions; API consumers must release the service
when the plugin unloads.

Violation, exemption, flag, setback and punishment methods/events arrive with the
corresponding working engines in later phases. There are no placeholder APIs that
pretend those operations succeeded. This pre-release API can evolve before 1.0.


## Phase 2 snapshot additions

`PlayerSnapshot.connection()` exposes separate RTTs/jitter, token statistics,
sequence, packet rate/gap, processing delay and uncertainty. It can be null during
initial transport binding. `movement()` exposes partial-field presence and raw
client position/rotation/ground claims. `activity()` includes action flags,
client-vs-server flight state, inventory metadata, interaction timestamps,
local impulse time and last sent/matched teleport identifiers. Read the records'
JavaDocs and [PHASE2.md](PHASE2.md) before using these as inputs to a future check.

A loss epoch mismatch means the worker's state predates discarded input. Even
when the epochs match, `uncertain()` can remain true during grace or a stall.
A known position is not a verified safe position. Packet history is bounded and
can reset; it is not a complete replay log. RTT is observation-based and does not
prove physical write completion or establish an acknowledgement fence.

The pre-release record constructors changed in 0.2.0. Integrations compiled
against 0.1.0 should be rebuilt against the matching API module. Query method
names remain unchanged. There is still no flag/reset-violations/punishment API.

## Phase 3 physics snapshots

`PlayerSnapshot.physics()` returns an immutable `PhysicsSnapshot` with the packet
sequence, world revision, exact model name, candidate count, best candidate
coordinates, inferred ground contact, residual distance and immutable uncertainty
reason names. Zero candidates and residual -1 mean unavailable. It never exposes
Bukkit objects. Residuals are experimental diagnostics, not violations or legal
movement envelopes. Ingress loss, world invalidation and snapshot expiry hide
obsolete predictions immediately at the next API read.

Every runtime comparison includes `UNACKNOWLEDGED_WORLD` and `INPUT_INFERRED`;
unknown edition also includes `UNKNOWN_EDITION`. See [PHASE3.md](PHASE3.md) before
using this data. Record constructors changed again in 0.3.0; rebuild pre-release
integrations. The three service query method names remain unchanged.

## Phase 4 movement-check snapshots

`PlayerSnapshot.movementChecks()` returns generation, immutable per-ID
`CheckSnapshot` records, bounded `MovementEvidence`, and an optional
`SafePositionSnapshot`. Evidence contains numeric observed/expected/excess values,
packet sequence, monotonic observation time, configuration generation, diagnostic
classification and uncertainty reasons. These snapshots remain separate from the Phase 8 persistent log and score API.
Findings are experimental, not commands to punish.

`safePosition.verified()` must be checked independently of presence. Runtime
geometry currently produces **unverified candidates only**. A candidate stores
world/session identity, revision, timestamp, coordinates and reasons; it is hidden
on revision mismatch or expiry. Even a future verified snapshot must be
revalidated on the owning thread before an action. This release performs no action.

Loss, close and pending configuration changes hide obsolete check state in API
reads. Per-check status describes the last processed evaluation, not proof that a
currently idle player remains eligible. The pre-release `PlayerSnapshot` constructor
changed in 0.4.0; rebuild integrations. Service query methods remain unchanged.

## Phase 5 combat snapshots

`PlayerSnapshot.combat()` returns immutable `CombatSnapshot`: generation, observed
attack/swing counts, tracked-target count, recent interval-derived swing rate,
interval variation, check states and bounded `CombatEvidence` records. Swing rate
expires after one second of inactivity; it does not measure physical CPS.

Evidence contains check ID, source attack/packet sequence, observation time,
configuration generation, target entity ID (-1 if not applicable), numeric
observed/expected/excess values, diagnostic classification and immutable reasons.
Deferred swing checks retain the attack sequence but use the later expiration
observation time. Counters and history reset on lifecycle/configuration changes.
Empty snapshots hide closed sessions, packet-loss epochs and pending generations.

Live combat evidence always includes uncertainty and is diagnostic. It is not an
authorization for cancellation or punishment. Rebuild API consumers for 0.5.0:
`PlayerSnapshot` adds a combat component to its pre-release record constructor.
The service query methods are unchanged.

## Phase 6 guard snapshots

`PlayerSnapshot.guard()` publishes immutable `GuardSnapshot`: configuration
generation, per-ID `CheckSnapshot` values and a bounded combined evidence list.
`GuardEvidence` includes check, category, packet sequence, observation time,
generation, finite observed/limit values and an immutable reason set. Evidence is
sorted by source sequence; equal-sequence entries do not have a severity ordering.
Each category has its own capacity, so one category cannot evict another's records.

Guard evidence always contains `OBSERVATION_ONLY`; buffers/findings are always zero.
`UNAVAILABLE` identifies an unimplemented model, and enabling it is rejected. Loss,
reload, teleport/world/protocol reset and quit clear or immediately hide stale state.
No API call authorizes enforcement. The added `guard` record component changes the
pre-release `PlayerSnapshot` constructor in 0.6.0; rebuild integrations. The service
query methods are unchanged.

## Phase 7 edition and translated observations

`PlayerSnapshot.edition()` returns immutable `EditionSnapshot`: status, source,
device, input, version, monotonic observation time and reason set. UNKNOWN is never
proof of Java. JAVA can depend on an explicit administrator topology assertion;
positive official results identify BEDROCK. Device/input/version are optional,
client-reported, bounded labels. No XUID/address is exposed.

`PlayerSnapshot.bedrockAnalysis()` returns immutable `BedrockSnapshot`: generation,
status, reasons and six lane lists keyed by movement, rotation, input, inventory,
combat and placement. Each observation has sequence, monotonic time, bounded action
and finite scalar values. Default cap is 16 per lane, maximum 64. These are translated
observations, not native client inputs or proven accepted actions.

Snapshots hide stale, provider-invalidated, identity-changed, lost or reloaded
analysis before the worker's next packet. Confirmed Bedrock physics is unavailable
with zero Java candidates. Same-identity polling refreshes age without clearing
histories. All values remain diagnostic; see [BEDROCK.md](BEDROCK.md).
This extends the pre-release `PlayerSnapshot` constructor in 0.7.0; rebuild API
integrations. The Bukkit service interface remains read-only.

## Phase 8 scores and events

`AntiCheatApi.violations(UUID)` returns immutable `ViolationSnapshot` with total risk,
confidence, retained finding count, per-check decaying VL and per-category risk.
Missing, closed or invalidated sessions return an empty snapshot. Calls are thread-safe;
values are policy scores, not calibrated cheat probabilities. Diagnostics never add VL.

The Paper adapter exposes synchronous `dev.aegisac.paper.event.PlayerFlagEvent`,
`PlayerPunishEvent` and `PlayerSetbackEvent` (cancellable), plus read-only
`ViolationChangeEvent`. Each carries an immutable `OutputRecord` and score snapshot.
Flag cancellation suppresses scoring/output. Action cancellation suppresses effects;
a cooldown is still reserved for punishment to prevent repeated listener calls.
Listeners must return promptly. Revalidation follows listeners; cancellation removal
cannot bypass trust, session, freshness, permission or panic gates.

Recompile integrations for the new pre-release interface method. There is no public
trusted-flag injection method or automatic conversion of diagnostic evidence.
See [PHASE8.md](PHASE8.md) for scoring, action and lifecycle contracts.

## Phase 9 staff state

The public score API and four output events retain their Phase 8 contracts. Staff
reset invalidates current analysis and scores and removes queued evidence; persisted
logs and punishment cooldowns remain. Temporary exemptions and freezes are internal
session controls, not a new public mutable API. Plugins must not edit private menu
holders or infer actions from inventory text. Bukkit inventory events are cancelled
for AegisAC menus, and any previously cancelled click is respected.

## Phase 10 scheduling boundary

The public API remains immutable. Internal scheduler adapters are not a new supported
external scheduling API. Bukkit events/effects are still production-supported only
on the conventional adapter; do not infer Folia support from packaged native adapter
classes. World uncertainty can include `REGION_NOT_OWNED`. Consumers must treat any
uncertainty as a gate, not a trusted empty geometry sample.

Source-player events and recipient delivery now use separate owner contexts internally.
Listeners must not inspect foreign live entities or block waiting on another region.
Only conventional Paper is enabled in this build; the native integration remains gated.
