# Phase 9 validation

Validated on 2026-10-08 (UTC) with JDK 21 and Gradle 9.8.0.
Historical Phase 1–8 reports remain in `docs/validation/`.

## Executed build

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 57s; all 21 actionable tasks executed.**
**461 tests passed; 0 failures, 0 errors, 0 skipped.**
Common executed 353 tests; Paper executed 108.
The API module has no test sources; NO-SOURCE is not counted as validation.
Clean removed earlier results and build-cache reuse was disabled. These results
supersede intermediate runs. No dependencies, locks or verification entries changed
for Phase 9.

Log: `/workspace/.tools/aegis-phase9-verified.log`. XML/HTML reports are under
each module's `build/test-results/test` and `build/reports/tests/test`.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| BedrockMonitorTest | 4 | 0 | 0 | 0 |
| EditionPolicyTest | 13 | 0 | 0 | 0 |
| IdentityResolverTest | 5 | 0 | 0 | 0 |
| CheckBufferTest | 3 | 0 | 0 | 0 |
| MovementDispatcherTest | 6 | 0 | 0 | 0 |
| MovementEvaluatorTest | 28 | 0 | 0 | 0 |
| MovementMonitorTest | 6 | 0 | 0 | 0 |
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
| ConfigurationTransactionTest | 20 | 0 | 0 | 0 |
| GuardConfigurationTest | 15 | 0 | 0 | 0 |
| MovementConfigurationTest | 14 | 0 | 0 | 0 |
| PhysicsConfigurationTest | 9 | 0 | 0 | 0 |
| PipelineConfigurationTest | 12 | 0 | 0 | 0 |
| ConnectionTrackerTest | 5 | 0 | 0 | 0 |
| TransactionTrackerTest | 7 | 0 | 0 | 0 |
| GuardDispatcherTest | 3 | 0 | 0 | 0 |
| GuardMonitorTest | 17 | 0 | 0 | 0 |
| RateWindowTest | 3 | 0 | 0 | 0 |
| OutputPolicyTest | 28 | 0 | 0 | 0 |
| BrandDecoderTest | 1 | 0 | 0 | 0 |
| SerialPacketQueueTest | 5 | 0 | 0 | 0 |
| PhysicsReplayTest | 28 | 0 | 0 | 0 |
| EditionPipelineTest | 8 | 0 | 0 | 0 |
| MovementCheckPipelineTest | 4 | 0 | 0 | 0 |
| PacketStateTest | 8 | 0 | 0 | 0 |
| PhysicsPipelineTest | 5 | 0 | 0 | 0 |
| PlayerLifecycleTest | 9 | 0 | 0 | 0 |
| WorldViewTest | 2 | 0 | 0 | 0 |
| BootstrapTest | 12 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 16 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| AdminMenuTest | 25 | 0 | 0 | 0 |
| IdentityServiceTest | 6 | 0 | 0 | 0 |
| OfficialIdentityProviderTest | 5 | 0 | 0 | 0 |
| AsyncLogStoreTest | 4 | 0 | 0 | 0 |
| OutputServiceTest | 14 | 0 | 0 | 0 |
| VerifiedSetbackTest | 4 | 0 | 0 | 0 |
| WebhookWorkerTest | 4 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 3 | 0 | 0 | 0 |
| WorldCaptureTest | 9 | 0 | 0 | 0 |

## Packaged artifact

- Deployable file: `aegis-paper/build/libs/AegisAC-0.9.0-SNAPSHOT.jar`
- Size: 12842775 bytes
- SHA-256: `6994a00d13de415a92f5354816dcc2d214fb7687670425918164aa599a524b88`

`verifyPluginJar` loaded the actual shaded jar with only a JDK parent, generated all
24 YAML documents and opened SQLite with the bundled driver. Server APIs and
PacketEvents remain external; relocated YAML, JDBC/native resources and licenses
were checked by the distribution verification task.

## Phase 9 coverage

- Validated edits persist across fresh loads, retain exact-byte comment backups,
  preserve unrelated policy and reject unsupported paths/unavailable models.
- Generation conflicts, changed disk settings, edits during final authorization,
  revoked authorization and symbolic backups cannot publish an edit.
- GUI schema tests reject overlapping/duplicate/out-of-range slots, invalid materials,
  unsupported links and bad text/list values. Legacy disabled GUI settings are preserved.
- All thirteen menus open in MockBukkit; valid check, global/per-check punishment,
  default/per-world profile edits persist through the worker and owner-thread authorization.
- Commands and completion enforce granular permissions. Actual bypass permission
  does not grant bypass management. Category filtering and expanded profiles are exercised.
- Shift/number-key/double-click/offhand/drop/middle/outside clicks, bottom-inventory
  clicks and drags cannot transfer items or mutate configuration. Item names cannot
  spoof server actions. Repeated clicks schedule at most one action per menu per tick.
- Reloads, permission loss, replaced target sessions, closed menus and old-view replay
  block GUI mutations. Right-click punishment requires its independent permission.
- Timed exemptions expire, are check-specific and do not survive reconnect. Freeze
  holds translation, allows rotation and releases on unfreeze, expiry, teleport and
  shutdown while preserving independent exemptions. Manual monitor bypasses suppress findings.
- Reset discards current history/queued output. Exemptions installed during a flag
  event prevent scoring/effects. Recent history is bounded to 32 immutable records.
- Additional aliases preserve conflicts, delegate to real command handling and
  unregister only owned entries on forwarding command maps.

Initial obsolete tests that required GUI to remain disabled were updated to assert
invalid inventory layouts instead. MockBukkit does not implement `callSyncMethod`;
the final implementation uses a scheduled owner task plus a bounded worker future.
Alias cleanup initially used iterator removal, which failed on the forwarding map;
owned entries are now removed by key. No test was disabled or skipped to obtain
these results. Earlier phases' regression suites also passed.

## Limits

No live Minecraft server, real Java/Bedrock client, production ban plugin, live
Discord endpoint or Folia scheduler was validated. Baseline remains Paper API
1.21.11/Java 21; Paper 26.2/Java 25 remains unverified. The earlier console establishes
missing standalone PacketEvents before AegisAC enablement; no remote server was changed.

Difficulty profiles are observe-only labels, not threshold multipliers. GUI control
of punishment does not remove experimental opt-in or evidence/identity/permission
gates. Automatic punishment and setbacks default off; diagnostics cannot score or
punish. Runtime inferred/unacknowledged physics cannot produce verified safe positions.
Native Bedrock physics, unavailable check models, MySQL and full correction modes
remain separate work. See PHASE9.md for the temporary freeze's exact scope.

Configuration transactions compare source bytes before atomic replacement; they do
not provide an OS compare-and-swap against a racing external editor. Backup and target
moves are individually atomic. GUI history is bounded session output, not a durable
archive. The file editor reformats only its target and preserves the previous file
in `.last-admin-edit.bak`. Existing non-failing API deprecations, JVM class-sharing
warnings and Gradle's future-version notice remain on the pinned toolchain.
