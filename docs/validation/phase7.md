# Phase 7 validation

Validated on 2026-10-07 with the prepared JDK 21 and Gradle 9.8.0 toolchain.
Historical Phase 1–6 reports remain in `docs/validation/`.

## Executed build

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 24s; all 21 actionable tasks executed.**
**355 tests passed; 0 failures, 0 errors, 0 skipped.**
Common executed 299 tests and Paper executed 56. The API module has no test sources;
its NO-SOURCE task is not counted as testing. The clean build removed earlier
results and disabled build-cache reuse.

Log: `/workspace/.tools/aegis-phase7-clean.log`. XML and HTML reports are under each
module's `build/test-results/test` and `build/reports/tests/test`.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| BedrockMonitorTest | 4 | 0 | 0 | 0 |
| EditionPolicyTest | 13 | 0 | 0 | 0 |
| IdentityResolverTest | 5 | 0 | 0 | 0 |
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
| EditionPipelineTest | 5 | 0 | 0 | 0 |
| MovementCheckPipelineTest | 4 | 0 | 0 | 0 |
| PacketStateTest | 8 | 0 | 0 | 0 |
| PhysicsPipelineTest | 5 | 0 | 0 | 0 |
| PlayerLifecycleTest | 7 | 0 | 0 | 0 |
| WorldViewTest | 2 | 0 | 0 | 0 |
| BootstrapTest | 11 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 16 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| IdentityServiceTest | 6 | 0 | 0 | 0 |
| OfficialIdentityProviderTest | 5 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 3 | 0 | 0 | 0 |
| WorldCaptureTest | 9 | 0 | 0 | 0 |

## Packaged artifact

- Deployable file: `aegis-paper/build/libs/AegisAC-0.7.0-SNAPSHOT.jar`
- Size: 717002 bytes
- SHA-256: `2608991d591c2e98ffdab346599e052e986f9dc4d42ff1de23ff1b14bbe1f9de`

`verifyPluginJar` passed against the actual shaded jar with a JDK-only parent
classloader, including the relocated configuration engine and all 24 generated
YAML documents. PacketEvents and server APIs remain external dependencies.
Optional Floodgate/Geyser public APIs are linked reflectively; no provider is bundled.
No dependency version, checksum or lockfile change was needed for Phase 7.

## New verification

- Official-shaped Floodgate/Geyser API fixtures: positive and negative results,
  null singleton, invocation failure, optional metadata failure, bounded version
  labels, missing classes, incompatible return signatures and nullable connection.
- Positive precedence, secondary failures, explicit Java-only assertion, complete
  negative-provider coverage and a positive-session latch surviving provider loss.
- Owner-thread enforcement, eight-session query budget, round-robin coverage,
  stale/future identity, backwards clock, provider epoch, reload, reconnect and close.
- All 72 edition rules, conservative UNKNOWN policy, separate Java enable flags,
  manual sensitivity/tolerance overrides, global and legacy disable precedence,
  atomic rejection of invalid settings and old-file merge without rewrite.
- Identical movement bursts produce Java diagnostics at the Java limit while
  staying below the adjusted Bedrock/UNKNOWN limit. Both remain zero-findings.
- Six independent translated lanes, maximum sizes, expiry, nonfinite sanitization,
  immutable snapshots, generation/teleport/time/identity resets and Java exclusion.
- Session transitions hide obsolete snapshots before the next packet, invalidate
  motion/world baselines, preserve history across same-identity polling and reject
  late publication to closed sessions. Confirmed Bedrock returns zero Java candidates.
- Separate command permissions, completion and unknown/unavailable diagnostics.

Earlier failing Java pipeline fixtures had unspecified identity; they now explicitly
publish Java identity. Startup still reports BASELINE_MISSING before analysis exists.
A malformed intermediate default sensitivity was corrected and all YAML generation
was revalidated. The packet decoder fixture now uses non-recording mocks for API and
Netty dispatch: this prevents test-only heap exhaustion while PacketEvents initializes
its item registry, preserving real buffer decoding. No assertion was disabled or skipped.

## Limits

No live Minecraft server or real client was run. API-shaped fixture tests and public
source contract inspection do not establish actual provider/plugin-classloader
compatibility, proxy forwarding, linked-account deployments, touch/controller traces,
production accuracy, Paper 26.2/Java 25 compatibility or Folia support.
Native Bedrock physics is unavailable; the new engine analyzes translated observations.
UNKNOWN does not establish Java. Administrator topology assertions are configuration
claims, not network discovery. Earlier unavailable models remain unavailable.
There is no alert/punishment/enforcement path.

The user-supplied Phase 5 console log reported missing `packetevents` before AegisAC
enablement. It must be installed separately. The obsolete `potion` save-limit key and
missing EconomyShopGUI alias were separate server configuration issues. This work
has not modified or certified that remote server. See TROUBLESHOOTING.md.

Existing test API deprecations (`PlayerQuitEvent`, `ClientVersion.toServerVersion()`),
Mockito's class-sharing warning and Gradle's future-10 deprecation notice remain
non-failing on the pinned build.
