# AegisAC

An original Minecraft anti-cheat project developed in independently verifiable
phases. **Phase 10 adds explicit scheduling, entity-owned service handoffs, immutable
player observations, region-local health and asynchronous correction support. Native
lifecycle acceptance and independent live Paper/Folia validation remain unfinished;
Folia loading is deliberately blocked.**
This is not a validated production protection release.

Read [ARCHITECTURE.md](ARCHITECTURE.md) for the project tree, ownership model and
complete twelve-phase roadmap. All source is in this repository; no proprietary
anti-cheat source or algorithms were copied.

## Phase 10 ownership milestone

- Explicit global, entity, region and async scheduling with bounded admission and cancellation.
- Independent native-Folia API fixtures alongside conventional Bukkit scheduler tests.
- Session-addressed asynchronous delivery prevents replies from reaching replacement sessions.
- Concurrent capture coordination, one pending capture per session and ownership checks before geometry reads.
- Concurrent identity, source/recipient output routing, remote staff controls and immutable profile observations.
- Native asynchronous setbacks and short-lived source authorizations for global console dispatch.
- [PHASE10.md](PHASE10.md) describes this milestone; [FOLIA.md](FOLIA.md) tracks native lifecycle and live acceptance gates.

## Download a build from GitHub

The verified workspace jar is also available in [downloads/](downloads/README.md).

Open the repository's **Actions** tab, select a successful **Build and download
AegisAC** run for the desired branch, and download **AegisAC-Phase10** under
**Artifacts** while signed in to GitHub. Extract the ZIP to obtain the plugin jar,
`SHA256SUMS` and the source commit/build-run information. Downloads are retained for
90 days. Failed builds do not publish a plugin artifact. The jar requires standalone
PacketEvents 2.14.0, and this snapshot keeps Folia disabled.

## Retained Phase 9 capabilities

- Granular staff commands, visible-player completion, debug, reset and current-risk ranking.
- Thirteen configurable menus with pagination, profiles and bounded violation history.
- Persistent check/punishment/profile edits with validation, generation checks and backups.
- Session-scoped timed exemptions and temporary staff freezes with lifecycle cleanup.
- Additional aliases with collision protection and owned-command cleanup.
- [PHASE9.md](PHASE9.md) documents controls, permissions, persistence and limitations.

## Retained Phase 8 capabilities

- Chat, console and action-bar alerts with hover and optional teleport-command suggestion.
- Bounded evidence delivery, per-check VL, category/total risk, confidence and decay.
- Asynchronous SQLite batches, retention, bounded JSONL rotation and Discord retries.
- Freshness/session/permission gates, cancellable events and panic mode before effects.
- Independent verified-position setback policy; other correction modes remain unavailable.
- [PHASE8.md](PHASE8.md) documents exact contracts, configuration, commands and limitations.

## Retained Phase 7 capabilities

- Official API identity with provider failure, expiry, reconnect and reload handling.
- All 72 checks expose supported, adjusted or disabled Bedrock policy with manual overrides.
- Six independent translated histories; confirmed Bedrock bypasses Java physics.
- `/ac edition <player>` and `/ac bedrock <player>` with granular permissions.
- [PHASE7.md](PHASE7.md) and [BEDROCK.md](BEDROCK.md) document topology configuration and limits.

## Retained Phase 6 capabilities

- Owner-thread block range/eye sampling, bounded captured-world geometry and action matching.
- Inventory menu/slot observations, finite-value and sequence checks, bounded packet/use/placement rate windows.
- Per-category evidence and cooldowns; uncertainty cannot become a trusted finding.
- Five live configuration categories and `/ac inspect <player> [category]`.
- [PHASE6.md](PHASE6.md) documents exact signals and eight missing models; full Phase 6 coverage remains unfinished.

## Retained Phase 5 capabilities

- Per-viewer target spawn, movement, interpolation, teleport and removal history.
- Explicit limits for observed ping acknowledgement, delivery, pose and target dimensions.
- Reach, hitbox, aim/aim-assist, rotation, statistical clicking, aura, criticals,
  velocity/anti-knockback, swing, attack-pattern, timing and invalid-interaction diagnostics.
- Bounded evidence and matching windows; atomic configuration and lifecycle resets.
- `/ac combat <player>` and `/ac cps <player>`, immutable combat API snapshots and granular permissions.
- [PHASE5.md](PHASE5.md) documents each contract and remaining prerequisites for trusted detection.

## Retained Phase 4 capabilities

- Thirteen geometry/context evaluators plus movement-only Timer and Blink analysis.
- Independent configurable buffers, time-based decay, sample requirements and cooldowns.
- Diagnostic evidence kept separate from trusted findings; uncertainty never adds buffer.
- Join, teleport, velocity and lag grace; owner-thread permission/world/gamemode observations.
- Collision-clear safe-position candidates with explicit trust, revision and expiry.
- Atomic check-policy reloads, bounded evidence, and `/ac checks`, `/ac movement`, `/ac safeposition`.
- [PHASE4.md](PHASE4.md) documents exact coverage and the three unavailable models.

The earlier physics and packet foundation provides:

- Bounded, immutable world captures on the server thread, with revision fences,
  chunk/block invalidation, TTL expiry and fair per-tick capture budgets.
- Swept AABB collision resolution, partial shapes and step attempts, plus three
  explicit Java protocol models for deterministic movement experiments.
- Up to 18 inferred input candidates per position report and `/ac physics`
  diagnostics. Unsupported mechanics and unacknowledged world state remain uncertain.
- Synthetic movement replays and capture lifecycle tests; see [PHASE3.md](PHASE3.md).

The packet and configuration foundation also provides:

- Gradle Kotlin DSL multi-module build: API, common core, Bukkit/Paper adapter.
- Separate PacketEvents plugin integration with owned listener cleanup.
- Bounded per-player serial queues, a bounded worker pool, fair processing batches,
  visible loss epochs, and queue/decode/processing health metrics.
- Normalized packet data for movement, actions, inventory, teleports, velocity,
  effects, timing and acknowledgements; no raw buffers cross to worker threads.
- Separate keep-alive and transaction RTT, jitter, packet gaps, stall uncertainty,
  duplicate/out-of-order/expired token handling, and opt-in modern ping probes.
- Immutable protocol capability mapping, bounded client-brand decoding and recent
  packet history. Unknown clients are never assumed to use current physics.
- Transport identity checks prevent late packets from updating a new session.
- Twenty-four commented YAML files, bounded safe parsing, immutable configuration
  generations and atomic hot reload. Invalid reloads preserve active settings.
- Version-zero migration with backups; normal reloads preserve file contents.
- Administrative commands, granular permissions and permission-aware completion.
- Read-only Bukkit service API and bounded asynchronous configuration work.
- JUnit tests for core state, YAML, migrations, concurrency, plugin bootstrap and
  packet adapter behavior, real-buffer decoder fixtures, and an isolated test of the actual shaded jar.

## Requirements and build

Use a **JDK 21** (not just a JRE). The checksummed Gradle 9.8.0 wrapper is included.
Dependency versions and official research sources are in [DEPENDENCIES.md](DEPENDENCIES.md).

```sh
./gradlew build
```

The deployable file is `aegis-paper/build/libs/AegisAC-0.10.0-SNAPSHOT.jar`.
Do not install the `unbundled` or `sources` jars. PacketEvents **2.14.0** must be
installed separately. SnakeYAML is bundled and relocated; SQLite JDBC is bundled with native resources. Server APIs are not.

The initial API baseline is Paper **1.21.11** on Java 21. Bukkit-only platform
calls are chosen to make Spigot and Purpur validation possible. Compilation and
MockBukkit are verified; live Paper/Spigot/Purpur tests and newer server versions
remain unverified. Protocol identification is not a promise of legacy physics
support. Folia is deliberately not advertised or enabled; see [FOLIA.md](FOLIA.md).

## First start and commands

Follow [INSTALLATION.md](INSTALLATION.md). First enable generates files under
`plugins/AegisAC/`, validates all of them, attaches PacketEvents, registers player
sessions and reports optional plugin presence. Startup explicitly says `movement-evaluators=15 combat-evaluators=15`, `guard-evaluators=31`, `guard-unavailable=8`, `experimental=true`
and `diagnostics-never-punish=true`, along with punishment-policy and panic status.

| Command | Permission | Behavior |
| --- | --- | --- |
| `/ac help` | `aegisac.help` | Available commands |
| `/ac version` | `aegisac.version` | Version and phase |
| `/ac reload` | `aegisac.reload` | Asynchronous parse/validate and atomic publication |
| `/ac profile <player>` | `aegisac.profile` | Edition, timing, scores, effects, environment and recent alert |
| `/ac connection <player>` | `aegisac.connection` | RTT, jitter, pending tokens, loss and uncertainty |
| `/ac physics <player>` | `aegisac.physics` | Experimental model, candidates, residual and uncertainty |
| `/ac combat <player>` | `aegisac.combat` | Combat states and latest diagnostic evidence |
| `/ac cps <player>` | `aegisac.cps` | Observed swing interval statistics, not physical clicks |
| `/ac inspect <player> [category]` | `aegisac.inspect` | World/player/inventory/protocol/exploit diagnostics |
| `/ac edition <player>` | `aegisac.edition` | Edition provenance and optional device/input labels |
| `/ac bedrock <player>` | `aegisac.bedrock` | Translated history counts and uncertainty |
| `/ac checks [category]` | `aegisac.checks` | Experimental/disabled/unavailable catalog |
| `/ac movement <player>` | `aegisac.movement` | Per-check statuses and recent evidence |
| `/ac safeposition <player>` | `aegisac.safeposition` | Candidate coordinates, trust and reasons |
| `/ac alerts` / `/ac verbose` | `aegisac.alerts` / `aegisac.verbose` | Toggle staff finding/diagnostic subscriptions |
| `/ac violations <player>` | `aegisac.violations` | Current decaying session scores |
| `/ac logs <player-or-uuid>` | `aegisac.logs` | Asynchronous recent SQLite evidence query |
| `/ac panic` | `aegisac.panic` | Block automatic punishment; keep detecting/logging |
| `/ac output` | `aegisac.output` | Panic state and queue/I/O counters |
| `/ac performance` | `aegisac.performance` | Aggregate packet/session counters |

Phase 9 adds `/ac debug`, `toggle`, `reset`, `exempt`, `bypass`, `freeze`, `unfreeze`,
`top` and `gui`; see [the command/permission table](PHASE9.md#commands-and-permissions).

`/aegisac` aliases `/ac`. `aegisac.admin` grants all listed permissions; defaults
are operator-only. Additional aliases are configurable and require restart. The
`bypass` management command uses `aegisac.bypass.manage`; actual check bypass
permissions remain false by default.

Identity comes from official API observations. Non-positive results default to
**UNKNOWN** and the conservative Bedrock policy. Java-only servers must explicitly
assert `identity.java-only-server: true`; translated deployments may assert complete
provider coverage only when their topology supports it. Read [BEDROCK.md](BEDROCK.md)
before changing either assertion. Phase 8 actions require fresh, eligible Java evidence and explicit configuration; diagnostic evidence never qualifies.

## Documentation

[Configuration](CONFIGURATION.md) · [Checks](CHECKS.md) · [API](API.md) ·
[Bedrock](BEDROCK.md) · [Folia](FOLIA.md) · [Troubleshooting](TROUBLESHOOTING.md) ·
[Validation](VALIDATION.md) · [Packet contracts](PHASE2.md) · [Physics contracts](PHASE3.md) · [Phase 4 checks](PHASE4.md) · [Combat contracts](PHASE5.md) · [Phase 6 contracts](PHASE6.md) · [Phase 7 contracts](PHASE7.md) · [Phase 8 contracts](PHASE8.md) · [Staff administration](PHASE9.md) · [Scheduler hardening](PHASE10.md)

AegisAC source is GPL-3.0-only; see [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). PacketEvents remains a separately
installed GPL library/plugin. No detection performance or false-positive claim
is made by this foundation release.
