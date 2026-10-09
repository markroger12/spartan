# Phase 5: experimental combat analysis

AegisAC 0.5.0 adds 15 configurable combat evaluators and a bounded, per-viewer
entity history. They cover the requested combat categories, with anti-knockback
sharing VelocityA. They produce **diagnostics only in the live adapter**. These
are implemented packet/geometry/statistical signals, not certified cheat detection.
There is no attack cancellation, setback, kick, ban, global risk score or automatic
punishment. Production accuracy and complete client emulation remain unvalidated.

## Data flow and ownership

PacketEvents MONITOR listeners copy selected spawn, relative movement, movement
with rotation, entity teleport, absolute position sync, metadata/attribute change
and entity removal observations. The packet byte budget applies before decoding;
removal lists are capped at 1024 IDs. Wrappers, NBT, packet buffers, Bukkit entities
and worlds do not cross into the session worker. Existing cancellation and exact
transport/session checks apply to these new packets too.

`PlayerData` supplies a single configuration generation, current reported attacker
movement, connection state and owner-thread observations to `CombatMonitor`.
The owner thread reads eye height, the entity interaction range attribute and
explicit bypass permissions. It shares immutable scalar observations only. The
same round-robin budget as world capture applies: busy servers can have stale
owner observations, and missing, future or stale observations are explicitly gated.
No per-attack Bukkit lookup is performed.

Each viewer retains at most 64 targets with 8 position samples each by default.
Unknown-target movement never creates an entity. A spawn replaces an existing ID;
removal clears it. Teleports break interpolation. Unknown relative teleport flags,
invalid coordinates and backwards target timestamps discard the affected track.
Non-player entities are recorded as unsupported and receive no guessed geometry.
Capacity eviction, trimmed history, metadata changes and expiry are explicit.

An exact sampled ping/pong pair advances an **observed sequence marker**. Unknown,
ambiguous, duplicate or out-of-order responses cannot advance it. This is not a
socket-write fence: observing an outgoing packet in a callback does not prove its
relative delivery/execution at the client. Legacy window acknowledgements are not
used for combat markers. No modern marker is invented for unsupported clients.

## Geometry and timing limits

ReachA computes the minimum eye-to-expanded-box distance over retained target
alternatives; HitboxA performs slab ray intersections using reported yaw and pitch.
Adjacent positions within the interpolation window contribute their union, so a
plausible interpolated position can prevent a diagnostic. Target history defaults
to two seconds, independent of the measured RTT. RTT uncertainty gates evidence;
it does **not** select a supposedly exact rewind timestamp. The attacker origin is
the latest fresh reported movement plus the sampled server eye height. This is not
full attacker rewind or an authoritative client eye position.

Player targets use nominal 0.6 by 1.8 boxes with configurable expansion. Actual
pose, scale, mounts, protocol-specific interpolation and client-applied target
attributes are unconfirmed. Metadata/attribute changes add an explicit reason.
Every such geometry evaluation includes `TARGET_DIMENSIONS_UNCONFIRMED` and
`UNCONFIRMED_DELIVERY`. Swept boxes prefer missed detections over invented precision.
Exact pose/scale history and acknowledged delivery are prerequisites for trusted
reach findings, and remain outstanding.

Swing matching accepts one main-hand swing before **or** after one attack, within
200 ms by default. Each swing is consumed once. Expiration uses later packet
observation timestamps, never a delayed worker's wall clock. Pending attacks and
unmatched swings are bounded; overflow discards ambiguous matching state.
NoSwing, AttackTiming and KillAura evaluate only after this window. Their evidence
uses the original attack sequence and the expiration observation time. Original
and current gates/bypasses are combined so delayed work cannot escape an exemption.

The click and rotation windows use fixed-size primitive rings, sample variance,
coefficient of variation and repetition near the mean. The auto-clicker signal
requires both low variation and repeated intervals after enough samples. High
CPS alone does not trigger it. These are packet swing intervals, not measured
physical clicks. Burst, quantization, duplicate-sequence and device-specific
models are not claimed; legitimate clicking can produce similar statistics.
AttackTiming additionally requires a missing matched swing. `/ac cps` reports the
recent interval-derived swing rate, falling to zero after one second of silence.

VelocityA projects reported displacement onto the observed local server impulse.
Other entities' impulses are ignored. This is an anti-knockback diagnostic input,
not a tick-by-tick velocity simulation. Client application, collisions, friction,
stacked impulses and damage acceptance are unconfirmed. CriticalsA counts distinct
small airborne movement descents at attack time; it never claims critical damage
was applied. Aim and aura signals are similarly correlated; do not treat their
counts as independent evidence multipliers.

## Policy, state and bounds

All runtime combat evaluations include `CLIENT_ACTIONS_UNCONFIRMED`. Unknown edition,
protocol, connection uncertainty, server tick health and owner state contribute
additional reasons. Explicit world, gamemode, flight and per-check permissions,
join/reset grace and special movement suppress diagnostics. Any uncertainty resets
the corresponding buffer to zero; only a pure trusted test fixture exercises the
experimental buffered-finding path. There is no runtime switch that removes the
observation-only gate.

`CombatDispatcher` owns independent buffers, decay, consecutive sample requirements,
cooldowns and a 32-record evidence ring. The snapshot API publishes immutable counts,
check states, swing statistics and evidence. Generation changes, packet loss,
protocol/world reset, player teleport, invalid movement and backwards observation
time clear combat state. Quit clears all retained state. API reads immediately hide
state pending a loss epoch or new generation, even before the next worker packet.
Ordinary chunk/block updates preserve entity history and invalidate pending velocity
comparison; they are not player world transitions.

`checks/combat.yml` remains config-version 1. Missing keys inherit validated defaults;
existing `enabled: false` remains false and the file is not rewritten. Maximum
targets, samples per target, pending attacks and statistics capacity require restart.
Other combat thresholds and rules reload atomically. All values have validated
bounds; unknown IDs, nonfinite values and unsupported Bedrock modes are rejected.
Per-check `bedrock-mode` accepts `diagnostic` or `disabled`; adjusted enforcement
awaits the separate Bedrock phase. Difficulty profile names do not multiply rules.

New commands are `/ac combat <player>` and `/ac cps <player>`, protected by
`aegisac.combat` and `aegisac.cps`. `/ac checks` now lists 33 entries: 15 movement,
15 combat and three unavailable movement models. Combat bypass nodes follow
`aegisac.bypass.<lowercase-id>` and default false, including for administrators.
The API adds `CombatSnapshot` and `CombatEvidence` to `PlayerSnapshot`; integrations
must rebuild for the changed pre-release record constructor.

## Build, source and verification

Complete source/configuration/test paths are in [PROJECT_TREE.md](PROJECT_TREE.md).
[CHECKS.md](CHECKS.md) lists each implemented signal. [CONFIGURATION.md](CONFIGURATION.md)
describes policy, [API.md](API.md) the snapshots, and [DEPENDENCIES.md](DEPENDENCIES.md)
the unchanged pinned dependencies. This phase adds no library or dependency lock change.

Use Java 21 and run `./gradlew clean build --no-build-cache --console=plain`.
The deployable jar is `aegis-paper/build/libs/AegisAC-0.5.0-SNAPSHOT.jar`.
[VALIDATION.md](VALIDATION.md) records the executed tests and artifact checksum.
Tests cover pure geometry, conservative target alternatives, acknowledgement
ambiguity, bounded histories, swings, all combat signals, suppression, lifecycle,
configuration, commands and actual PacketEvents adapters. Live Paper/Spigot/Purpur,
ViaVersion, Bedrock clients and production false-positive rates remain unverified.
Folia remains intentionally unsupported pending its separate phase.
