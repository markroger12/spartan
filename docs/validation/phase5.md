# Phase 5 validation

Validated on 2026-10-06 with JDK 21 and Gradle 9.8.0. Historical Phase 1–4
reports remain in `docs/validation/`.

## Executed build

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 25s; all 21 actionable tasks executed.**
**273 tests passed; 0 failures, 0 errors, 0 skipped.**
The common module executed 233 tests and Paper executed 40.
The clean build removed previous results and disabled build-cache reuse. The API
module has no test sources; its NO-SOURCE task is not counted as testing.

Log: `/workspace/.tools/aegis-phase5-clean.log`. XML/HTML reports are under each
module's `build/test-results/test` and `build/reports/tests/test`.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| CheckBufferTest | 3 | 0 | 0 | 0 |
| MovementDispatcherTest | 6 | 0 | 0 | 0 |
| MovementEvaluatorTest | 28 | 0 | 0 | 0 |
| MovementMonitorTest | 5 | 0 | 0 | 0 |
| MovementTimingTest | 5 | 0 | 0 | 0 |
| SafePositionTest | 2 | 0 | 0 | 0 |
| CollisionSolverTest | 9 | 0 | 0 | 0 |
| CombatDispatcherTest | 3 | 0 | 0 | 0 |
| CombatGeometryTest | 5 | 0 | 0 | 0 |
| CombatMonitorTest | 16 | 0 | 0 | 0 |
| SwingMatcherTest | 3 | 0 | 0 | 0 |
| TargetHistoryTest | 6 | 0 | 0 | 0 |
| CombatConfigurationTest | 13 | 0 | 0 | 0 |
| ConfigurationTest | 23 | 0 | 0 | 0 |
| MovementConfigurationTest | 14 | 0 | 0 | 0 |
| PhysicsConfigurationTest | 9 | 0 | 0 | 0 |
| PipelineConfigurationTest | 12 | 0 | 0 | 0 |
| ConnectionTrackerTest | 5 | 0 | 0 | 0 |
| TransactionTrackerTest | 7 | 0 | 0 | 0 |
| BrandDecoderTest | 1 | 0 | 0 | 0 |
| SerialPacketQueueTest | 5 | 0 | 0 | 0 |
| PhysicsReplayTest | 28 | 0 | 0 | 0 |
| MovementCheckPipelineTest | 4 | 0 | 0 | 0 |
| PacketStateTest | 8 | 0 | 0 | 0 |
| PhysicsPipelineTest | 5 | 0 | 0 | 0 |
| PlayerLifecycleTest | 6 | 0 | 0 | 0 |
| WorldViewTest | 2 | 0 | 0 | 0 |
| BootstrapTest | 9 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 14 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 2 | 0 | 0 | 0 |
| WorldCaptureTest | 9 | 0 | 0 | 0 |

## Artifact

- Deployable jar: `aegis-paper/build/libs/AegisAC-0.5.0-SNAPSHOT.jar`
- Size: 640308 bytes
- SHA-256: `ca814cad09879b24c7697d26ca74201caaf2ab92a17f5866463de56f6b85434c`

`verifyPluginJar` passed against the actual shaded jar with a JDK-only parent
classloader, including loading the relocated configuration engine and generating
all 24 default YAML documents. PacketEvents and server APIs remain external.
No dependency version, checksum or lockfile change was needed for Phase 5.

## What the new tests establish

- Ray/AABB tangency, parallel/behind rays, negative coordinates, yaw wrapping and
  conservative interpolation alternatives.
- Per-viewer target movement/removal, teleport discontinuities, unknown relative
  flags, metadata, expiry, target/history capacity and exact sampled ping markers.
- One-use swing matching before/after attacks, observed-time expiry, bounded overflow.
- Every combat signal's implemented predicates, statistical minimum samples,
  variable high-rate clicking without a CPS-only diagnostic, and combined attack timing.
- Diagnostic uncertainty cannot accumulate a runtime buffer/finding; grace and
  permissions suppress evidence, and pending attacks retain original exemptions.
- Local versus other entity impulses, protocol/queue lifecycle wiring, hidden stale
  snapshots on loss/reload, quit cleanup, malformed movement handling and rate expiry.
- Atomic configuration rejection, independent rules, restart-bound budgets, legacy
  disabled-file preservation, command permissions and operator bypass defaults.
- Real PacketEvents target wrappers, registry resources, Netty buffers, modern wire
  relative movement/removal, immutable copied output and removal-budget rejection.

Early runs caught the changed catalog size and incomplete PacketEvents test setup
(resource provider and Netty allocation). Those fixtures were corrected; the clean
result above supersedes the failed intermediate runs. An attack timing fixture was
also corrected to separate its two statistical windows with the documented silence.

## Limits and warnings

Tests use synthetic traces and mocked server APIs. No live server/client, proxy,
ViaVersion, Geyser/Floodgate or Folia conformance run was performed. Target delivery,
pose/scale, exact rewind, client eye state, critical damage and applied velocity
remain unconfirmed; runtime combat output stays diagnostic only. The full client
models and production false-positive/false-negative rates remain unvalidated.
[PHASE5.md](PHASE5.md) documents these limits; there is no enforcement.

Compilation reports two test-only deprecations: MockBukkit's `PlayerQuitEvent`
constructor and PacketEvents' `ClientVersion.toServerVersion()`. Mockito emits its
class-sharing warning. Gradle reports deprecated features affecting a future Gradle
10 upgrade. None failed the pinned Gradle 9.8.0 build.
