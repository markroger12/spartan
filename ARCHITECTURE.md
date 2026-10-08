# AegisAC architecture

AegisAC is an original, phased anti-cheat project. Phases 1–4 provide a working plugin, packet pipeline, experimental physics and
movement evaluators. Phases 5–9 add combat/guard diagnostics, edition policies,
bounded output and guarded opt-in actions, staff commands and persistent inventory controls.
Findings remain experimental; automatic punishment and setbacks default off.
No proprietary implementation or detection code is used.

## Project tree

The exact current file listing is in [PROJECT_TREE.md](PROJECT_TREE.md). The
following view groups files by architectural responsibility.

```text
.
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── aegis-api/src/main/java/dev/aegisac/api/
│   ├── AntiCheatApi.java
│   └── player/{PlayerSnapshot,ClientIdentity,BedrockStatus}.java
├── aegis-common/src/
│   ├── main/java/dev/aegisac/common/
│   │   ├── config/       # safe parsing, validation, migrations, atomic publication
│   │   ├── packet/       # normalization, bounded serial queues, processing and metrics
│   │   ├── connection/   # transaction correlation, RTT, jitter, stalls and uncertainty
│   │   ├── collision/   # swept AABBs, clipping and bounded step attempts
│   │   ├── check/       # movement evaluators, buffers, evidence, timing and safe candidates
│   │   ├── physics/     # versioned one-tick simulation, candidates and uncertainty
│   │   ├── world/       # immutable snapshots and CAS invalidation fences
│   │   └── player/       # concurrent registry, session state, immutable views
│   ├── main/resources/defaults/
│   │   ├── config.yml, messages.yml, performance.yml, compatibility.yml
│   │   ├── checks/{combat,movement,world,player,inventory,protocol,exploit}.yml
│   │   ├── profiles/{java,bedrock,strict,balanced,lenient}.yml
│   │   └── alerts.yml, punishments.yml, setbacks.yml, exemptions.yml,
│   │       storage.yml, webhooks.yml, gui.yml, logging.yml
│   └── test/java/        # real parser, filesystem, concurrency and lifecycle tests
├── aegis-paper/src/main/
│   ├── java/dev/aegisac/paper/
│   │   ├── AegisPlugin.java
│   │   ├── command/      # help, reload, version, profile, performance
│   │   ├── compatibility/ # capability discovery; no guessed Bedrock identities
│   │   ├── world/       # owning-thread capture, round-robin budget and invalidation
│   │   └── packet/      # external PacketEvents plugin adapter
│   └── resources/plugin.yml
├── .github/workflows/build.yml
└── README.md, INSTALLATION.md, CONFIGURATION.md, CHECKS.md, API.md,
    BEDROCK.md, FOLIA.md, TROUBLESHOOTING.md, DEPENDENCIES.md
```

## Dependency and ownership boundaries

`aegis-api <- aegis-common <- aegis-paper`. API and core have no Bukkit or
PacketEvents dependencies. The deployable jar includes the API, core and a
relocated SnakeYAML parser. Bukkit and PacketEvents remain server-provided.
PacketEvents owns its own injection and initialization; AegisAC registers and
unregisters only its listener and never terminates the shared engine.

One session exists per joined UUID. Join/quit own registry membership; packet
callbacks never create sessions. Late packets cannot resurrect disconnected
players. A session contains client identity, ordered connection/transaction observations,
raw movement/rotation, action/inventory/teleport state and bounded recent history.
Collision snapshots and diagnostic physics candidates are present; violation state awaits its producer. Public consumers receive immutable
snapshots, never mutable session objects or Bukkit entities.

Phase 2 callbacks validate a byte budget and decode selected PacketEvents wrappers
into immutable scalar records. The wrapper may use an earlier listener's cached
values, so legitimate packet modifications are honored. No raw buffers, wrappers,
item NBT or Bukkit objects are retained by workers. Opaque/unrelated traffic is
counted without decoding its body. Worker analysis is serialized per session;
capacity loss increments a visible epoch and invalidates incomplete history.
See [PHASE2.md](PHASE2.md) for exact packet coverage and timing limitations.

Configuration I/O uses a single bounded worker for command reloads. Build a full
candidate, validate every file, then publish one atomic reference. Errors retain
the previous snapshot. Defaults are installed only when absent. Version-zero
files migrate additively with a backup; renamed keys have explicit migration
rules. Existing comments are untouched on normal reloads. Migrations preserve
values and insert missing defaults; original comments remain in the backup.
Unsupported future versions and ambiguous rename conflicts fail explicitly.

## Complete development roadmap

| Phase | Deliverable and validation gate |
| --- | --- |
| 1 | Bootstrap, Gradle, packet lifecycle, sessions, safe configuration; compile and unit/integration tests. |
| 2 | PacketEngine adapters, normalized PacketListener/PacketProcessor, bounded ordered queues, TransactionTracker, ClientVersionProvider and ConnectionTracker. Test overflow, reconnect, RTT and acknowledgement ordering. |
| 3 | Collision AABBs and versioned physics. Capture world snapshots on owning region, bound scans, invalidate on block/chunk changes. Replay walking, sprint/jump, partial blocks, liquids, ice, slime, honey, ladders, pistons, elytra, vehicles, effects and velocity. Unsupported mechanics yield uncertainty, never violations. |
| 4 | Movement checks and safe positions: speed, fly, timer, blink, ground/no-fall, step, climb, liquid, no-slow, phase, acceleration, gravity, air-jump, sprint/sneak, elytra and vehicles. Per-check buffers, evidence, grace windows and legitimate trace regressions. |
| 5 | Combat: reach/hitbox using acknowledged target history and interpolation; rotation/aim, statistical clicking, aura, criticals, velocity, swing and attack sequencing. Multiple signals; no CPS-only punishment. |
| 6 | World, player, inventory, protocol and exploit checks: placement/digging/line-of-sight, scaffold/tower, item-use timing, slots, packet ordering, malformed inputs and bounded traffic handling. Cross-version negative tests. |
| 7 | Official Floodgate/Geyser identity adapters, separate translation-aware Bedrock policies and per-check SUPPORTED/ADJUSTED/DISABLED states. Bedrock replay corpus. |
| 8 | Multi-signal risk/confidence/VL decay, cancellable API events, configurable setbacks and punishment policy, panic mode, asynchronous SQLite batches, bounded HTTP webhook queue, retention and backpressure. Bans default off. |
| 9 | Full granular command suite, alerts/hover/click, editable inventory GUIs, persistent configuration transactions and profiles. Test permissions and inventory-generated events. |
| 10 | Folia entity/region/global/async scheduler adapters; audit every world/entity access and cross-region target snapshot. Run Paper and Folia independently before advertising support. |
| 11 | Development trace/replay, fixture corpus, JMH allocation/latency/throughput benchmarks and large-session load tests. Measure before optimizing hot loops. |
| 12 | Supported-version matrix, migration/upgrade fixtures, operational documentation, release signing/checksums and staged alert-only production observation. |

Every phase must compile before the next. Later phases extend narrow APIs rather
than replacing packet detection with Bukkit movement events. Each completed check
must document its mechanism, legitimate edge cases, exemptions, stability class,
Bedrock state, evidence and punishment recommendation. No accuracy or performance
claim is justified by compilation alone.

## State and execution model

Phase 2 implements connection/transaction/teleport/rotation/inventory observations. Phase 3
adds bounded world/collision snapshots, captured effects/attributes and experimental movement
simulation. Later phases add checks, acknowledged histories and complete special-mechanic models.
The check framework owns check state, exemptions and violations. A single serial
consumer owns each player's mutable simulation. Cross-player reads use immutable
timestamped histories. Region schedulers capture authoritative state and execute
setbacks; worker threads evaluate immutable inputs. Saturation drops analysis with
an explicit uncertainty/grace marker and metrics, never fabricates clean history.

Server/world/client version are separate concepts. Unknown client versions and
unknown Bedrock identity must remain explicit. High latency widens only checks
that require timing certainty, rather than disabling all protocol validation.

Persistence, webhook and trace queues are bounded and independently configurable.
Reloads publish configuration generations. Phase 2 pipeline budgets/timing settings
are startup-bound and cannot change underneath queued work; reload rejects changes
to that section. Future check dispatch will capture its configuration generation. Punishment and setback decisions recheck current policy,
session identity, exemptions and panic state on the appropriate scheduler.

## Phase 3 implementation

Read [PHASE3.md](PHASE3.md) for simulation contracts, supported baseline protocols,
uncertainty, capture budgets and explicit limits. The current non-Folia server
thread owns every world/entity read. Workers receive immutable numbers and boxes.
Geometry is server-observed, not the client's acknowledged world. Candidate
residuals never feed a detector, VL, safe-position store or enforcement path.

## Phase 4 implementation and remaining coverage

[PHASE4.md](PHASE4.md) defines the experimental movement layer: 15 implemented
evaluators and three explicitly unavailable entries (NoSlow, elytra, vehicles).
Every packet captures one immutable configuration generation. Publication of a
new generation resets buffers, timing debt, evidence and safe-position streaks.
Owner-thread policy observations and tick cadence cross to workers as immutable
records. Permission lookup never occurs on network or analysis threads.

Geometry remains client-reported and server-observed, not acknowledged by the
client. Runtime geometry can therefore produce diagnostics but not trusted buffer
increments or verified safe positions. The safe-position tracker supports trusted
fixtures/future inputs and never bypasses this prerequisite. Live trace validation,
acknowledged input/world history and the missing special models remain required
before the full Phase 4 movement coverage can be treated as validated protection.

## Phase 5 combat implementation

`common.combat` owns target history, pure ray/box geometry, bounded statistics,
one-to-one swing matching, per-player monitoring and independent check dispatch.
Packet adapters copy entity observations; owner-thread publication supplies eye
height, interaction range and permissions. No worker reads Bukkit state.
The common module adds validated `CombatSettings`; the API adds immutable
`CombatSnapshot`/`CombatEvidence`. [PHASE5.md](PHASE5.md) describes delivery uncertainty,
all 15 experimental signals, bounded resource/lifecycle rules and remaining models.
The runtime is diagnostic only; subsequent world/player/protocol, Bedrock,
risk/enforcement and Folia phases remain separate gates.

## Phase 6 implementation and missing prerequisites

`common.guard` contains `GuardMonitor`, `GuardDispatcher`, `GuardId`, fixed
`RateWindow` counters and immutable owner attributes. It consumes normalized packets
before mutable player state rejects invalid values, and reads only bounded immutable
world/owner observations. `GuardSettings` combines validated category policies;
`GuardSnapshot`/`GuardEvidence` expose diagnostics through the read-only API.
[PHASE6.md](PHASE6.md) details 31 implemented observations and eight unavailable
models. Full authoritative break, item-use, health, respawn, portal and item-component
coverage remains unfinished; no enforcement or production accuracy is claimed.

## Phase 7 implementation

`IdentityService` performs budgeted official API queries on the owner thread and
publishes immutable session-bound identity with configuration/provider epoch fences.
The worker derives edition policy once per change, resets cross-edition check state
and records six bounded translated histories for confirmed Bedrock only. API reads
hide stale analysis immediately. Confirmed Bedrock bypasses Java physics. Optional
reflection binds public contracts without bundling providers. See PHASE7.md and
BEDROCK.md for topology assertions, budgets, resets and live-validation limits.

## Phase 8 implementation and limits

`Detection` hooks feed immutable session-bound envelopes into a bounded owner-thread
`OutputService`. `ViolationLedger` accepts only uncertainty-free findings, bounds and
decays scores, while distinct asynchronous workers own SQLite/JSONL and HTTP queues.
Staff delivery, cancellable events, console command dispatch and verified-position
revalidation stay on the owner thread. Every action is gated again after listeners.
Panic blocks punishments without stopping detection or logs. See PHASE8.md for budgets,
configuration, correction prerequisites and unsupported modes. Native authoritative
physics, other cancellation modes and live accuracy remain outstanding. No false-positive
or protection guarantee follows from the fixture suite.

## Phase 9 administration

`AdminService` owns staff freezes and a one-worker/eight-waiting-edit queue.
`MenuService` owns at most 128 inventories, keeps slot actions independent of item
metadata, and rechecks generations/permissions/viewer and target sessions on the
server thread. Inventory transitions run next tick. `ConfigService` serializes
reloads and single-file edits; `ConfigurationTransaction` stages all documents,
validates schema/platform materials and detects observed external changes before
atomic backup/replace and snapshot publication. The final authorization handoff
waits only on the disk worker, never the server thread.

Temporary per-check exemptions belong to `PlayerData`, flow into every monitor,
and fence queued outputs. Reset invalidates old analysis, ledger/history and queued
notifications while preserving durable logs and punishment cooldowns. Alias cleanup
removes owned map entries by key; iterator removal is not sufficient for forwarding
command maps. See PHASE9.md for transaction boundaries and known limits.

## Phase 10 service ownership and release gates

`PlatformScheduler` separates global, entity, region and async domains. Conventional
production timers and async completion delivery use `BukkitPlatformScheduler`;
`FoliaPlatformScheduler` has an independent native-API fixture suite but is not
selected at runtime. `ManagedTasks` bounds admission and owns native cancellation,
including publication races. `SessionAudience` captures session identity and routes
replies to the same entity, with permissions checked on its owner.

`WorldCaptureService` coordinates a concurrent session map and short locked rotation
queue, dispatching at most one pending capture per entity. `WorldAccess` prevents
unowned geometry/entity reads. `REGION_NOT_OWNED` remains explicit uncertainty.
`PlayerDirectory` publishes immutable entity observations and region-local cadence.
Identity membership is concurrent; provider bindings remain global and publication
checks the pre-query epoch. Output effects use source owners, messages use recipient
owners, and plugin events execute outside ledger locks. Staff profiles use cached
observations; remote controls and freeze expiry use exact target sessions/records.
Native setbacks validate owned loaded geometry before async teleport. Console commands
cross from a source-owner grant to global dispatch with bounded age and data fences.

FOLIA.md audits native lifecycle/GUI teardown, actual dependency and command integration,
and independent Paper/Folia acceptance. The production support gate remains closed.
