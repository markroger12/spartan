# Phase 6 validation

Validated on 2026-10-06 with the prepared JDK 21 and Gradle 9.8.0 toolchain.
Historical Phase 1–5 reports remain in `docs/validation/`.

## Executed build

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 28s; all 21 actionable tasks executed.**
**316 tests passed; 0 failures, 0 errors, 0 skipped.**
Common executed 272 tests and Paper executed 44.
The API module has no test sources and its NO-SOURCE task is not counted as testing.
The clean build removed earlier results and disabled build-cache reuse.

Log: `/workspace/.tools/aegis-phase6-clean.log`. XML/HTML reports are under each
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
| GuardConfigurationTest | 15 | 0 | 0 | 0 |
| MovementConfigurationTest | 14 | 0 | 0 | 0 |
| PhysicsConfigurationTest | 9 | 0 | 0 | 0 |
| PipelineConfigurationTest | 12 | 0 | 0 | 0 |
| ConnectionTrackerTest | 5 | 0 | 0 | 0 |
| TransactionTrackerTest | 7 | 0 | 0 | 0 |
| GuardDispatcherTest | 3 | 0 | 0 | 0 |
| GuardMonitorTest | 17 | 0 | 0 | 0 |
| RateWindowTest | 3 | 0 | 0 | 0 |
| BrandDecoderTest | 1 | 0 | 0 | 0 |
| SerialPacketQueueTest | 5 | 0 | 0 | 0 |
| PhysicsReplayTest | 28 | 0 | 0 | 0 |
| MovementCheckPipelineTest | 4 | 0 | 0 | 0 |
| PacketStateTest | 8 | 0 | 0 | 0 |
| PhysicsPipelineTest | 5 | 0 | 0 | 0 |
| PlayerLifecycleTest | 7 | 0 | 0 | 0 |
| WorldViewTest | 2 | 0 | 0 | 0 |
| BootstrapTest | 10 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 16 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 3 | 0 | 0 | 0 |
| WorldCaptureTest | 9 | 0 | 0 | 0 |

## Packaged artifact

- Deployable file: `aegis-paper/build/libs/AegisAC-0.6.0-SNAPSHOT.jar`
- Size: 676364 bytes
- SHA-256: `2fab1d881ca4df55d73773240bab4976f08e7deed42c502d21a91d381b2732a6`

`verifyPluginJar` passed against the actual shaded jar with a JDK-only parent
classloader, including the relocated configuration engine and all 24 generated
YAML documents. PacketEvents and server APIs remain external dependencies.
No dependency version, checksum or lockfile change was needed for Phase 6.

## New verification

- All 31 implemented predicates through synthetic world/player/inventory/protocol
  and rate traces; unimplemented models report unavailable and cannot be enabled.
- Fresh eye/range geometry, captured occlusion/liquid observations, missing geometry,
  legitimate placement, legacy face-255 air-use, modern sequence gating and signed wrap.
- Matching dig/use/menu observations, unrelated closes, outside-click sentinel -999,
  finite diagnostic evidence for nonfinite client values and exact teleport confirmation.
- Rate warmup, bucket expiry, negative monotonic times, backwards-time reset, per-family
  windows, per-category bounded evidence, cooldowns and explicit exemptions.
- Atomic policy publication/rejection, old disabled-file preservation and immutable settings.
- Loss/reload/quit hiding, diagnostic evaluation before mutable state rejects malformed
  movement, command permissions/completion and bypass permissions defaulting false.
- Actual PacketEvents block/menu wrappers and held-slot wire bytes; owner-thread sampled
  block interaction range demonstrably changes geometry without worker Bukkit reads.

An early scaffold fixture was exactly on the excluded distance boundary; it was
corrected to exercise the intended below-feet case. The clean results above
supersede intermediate runs. No failing assertion was disabled or skipped.

## Limits and supplied console report

No live Minecraft server or real client was run in this environment. Synthetic
traces and mocked APIs do not establish production accuracy, ViaVersion/Geyser/
Floodgate behavior, Paper 26.2/Java 25 compatibility, or Folia support.
Eight Phase 6 models remain unavailable; their prerequisites are listed in PHASE6.md.
All Phase 6 evidence remains diagnostic and cannot trigger enforcement.

The user-supplied Phase 5 console log on Paper 26.2/Java 25 shows
`UnknownDependencyException: [packetevents]`. AegisAC was rejected before enablement;
bootstrap discovery is not successful plugin startup. This is a missing external
plugin, not evidence of an AegisAC code exception. The `potion` entity-save-limit
configuration error and missing EconomyShopGUI alias are separate server issues.
The required dependency remains declared, with installation/troubleshooting guidance
updated. No claim is made that the supplied server now starts or that those remote
configuration files were modified.

Two existing test API deprecations remain (`PlayerQuitEvent` and
`ClientVersion.toServerVersion()`), plus Mockito's class-sharing warning and Gradle's
future-10 deprecation notice. None failed the pinned Gradle 9.8.0 build.
