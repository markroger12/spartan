# Phase 8 validation

Validated on 2026-10-08 (Asia/Karachi) using JDK 21 and Gradle 9.8.0.
Historical Phase 1–7 reports remain in `docs/validation/`.

## Executed build

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 23s; all 21 actionable tasks executed.**
**410 tests passed; 0 failures, 0 errors, 0 skipped.**
Common executed 330 tests; Paper executed 80. The API module has no test sources;
NO-SOURCE is not counted as testing. Clean removed prior results and build-cache
reuse was disabled. These results supersede intermediate runs.

Log: `/workspace/.tools/aegis-phase8-verified.log`. Current XML/HTML reports are under
each module's `build/test-results/test` and `build/reports/tests/test`.

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
| OutputPolicyTest | 28 | 0 | 0 | 0 |
| BrandDecoderTest | 1 | 0 | 0 | 0 |
| SerialPacketQueueTest | 5 | 0 | 0 | 0 |
| PhysicsReplayTest | 28 | 0 | 0 | 0 |
| EditionPipelineTest | 8 | 0 | 0 | 0 |
| MovementCheckPipelineTest | 4 | 0 | 0 | 0 |
| PacketStateTest | 8 | 0 | 0 | 0 |
| PhysicsPipelineTest | 5 | 0 | 0 | 0 |
| PlayerLifecycleTest | 7 | 0 | 0 | 0 |
| WorldViewTest | 2 | 0 | 0 | 0 |
| BootstrapTest | 12 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 16 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| IdentityServiceTest | 6 | 0 | 0 | 0 |
| OfficialIdentityProviderTest | 5 | 0 | 0 | 0 |
| AsyncLogStoreTest | 4 | 0 | 0 | 0 |
| OutputServiceTest | 11 | 0 | 0 | 0 |
| VerifiedSetbackTest | 4 | 0 | 0 | 0 |
| WebhookWorkerTest | 4 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 3 | 0 | 0 | 0 |
| WorldCaptureTest | 9 | 0 | 0 | 0 |

## Packaged artifact

- Deployable file: `aegis-paper/build/libs/AegisAC-0.8.0-SNAPSHOT.jar`
- Size: 12789878 bytes
- SHA-256: `4c5a617e747085635acb6d00ea1bbdda29a8d4b79996249b597da1372d3a9ef1`

The actual shaded jar was loaded with only a JDK parent classloader. Its relocated
configuration loader generated all 24 YAML documents, and the bundled SQLite driver
opened a database and returned its version. JDBC service metadata, native resources
and both upstream license files are retained. PacketEvents/server APIs remain external.

SQLite JDBC 3.53.4.0 is the only new runtime dependency. Its jar/POM and the imported
JUnit 5.12.2 BOM POM were checked against Maven Central's HTTPS SHA-256 files. The
existing checksum entries were compared before/after: zero changed or removed.
Tests still resolve JUnit 6.1.3; the older BOM is metadata imported by SQLite's POM.
Locks include SQLite in Paper compile/runtime/test classpaths. The jar is larger
because the SQLite native resources are included.

## Verification added

- Actual guard evidence reaches output hooks exactly once, including after monitor
  replacement on configuration reload; diagnostics retain their ineligible status.
- Finite, uncertainty-free findings alone enter the bounded ledger; sequence
  deduplication, per-check/category/total scores, decay/expiry, confidence policy,
  window bounds, backwards time and experimental opt-in are tested.
- Invalid thresholds/commands, unavailable models, immutable command lists, endpoint
  redaction, manual policy changes and old-file preservation reject or merge atomically.
- Fresh synthetic eligible findings dispatch configured commands once; diagnostics,
  uncertainty, panic, stale evidence/configuration, packet loss, reconnect, provider
  epochs, changed world/check enablement, bypasses and cancellable events block them.
- Provider recovery with identical labels cannot revive old analysis or queued
  evidence. A loss during a packet's emissions cannot stamp older analysis with the
  newly observed loss epoch. Each analysis captures a single provider epoch.
- Staff subscription, diagnostic/normal channel separation, permission revocation,
  command permissions/completion, panic visibility and read-only score API are tested.
- Real SQLite prepared inserts, batching, UUID queries, maximum-row retention,
  JSONL rotation/size bounds, generation filtering, disabled diagnostics, unwritable
  destinations, accepted-write drain and closed-writer rejection are tested.
- Webhook fake transport verifies mention suppression, escaping, request spacing,
  bounded retries, permanent-failure handling, stale-generation rejection, overflow
  and interruption on close. No request was sent to Discord.
- Verified-position fixtures test same-world/session/revision/time gates, owner-thread
  enforcement, current collision rescan, no unloaded-chunk reads, bounded distance,
  and rejection of unverified, stale or invalid candidates. Unsupported correction
  modes are rejected in configuration.

The initial Gradle verification-metadata write failed on duplicate pre-existing Paper
snapshot keys. Independently verified entries were added directly without weakening
verification. A Kotlin DSL namespace conflict in the added SQLite smoke check was
fixed. Existing tests for reserved subsystems were updated to exercise still-unavailable
GUI settings. No assertion was disabled or skipped to obtain the final results.

## Limits

No live Minecraft server, real Java/Bedrock client, live Discord endpoint, production
ban plugin or Folia scheduler was validated. Paper API baseline remains 1.21.11/Java 21;
Paper 26.2/Java 25 compatibility remains unverified. The supplied Phase 5 console still
establishes only a missing standalone PacketEvents dependency, not successful startup.
No remote server configuration was changed by this work.

Automatic punishments and setbacks default off. Diagnostic evidence never scores or
authorizes effects. Existing runtime physics remains inferred/unacknowledged and cannot
produce a verified safe position. Native Bedrock physics, other cancellation modes,
MySQL/MariaDB and full GUI/command management remain unavailable. Synthetic verified
fixtures prove the gate/dispatch behavior, not real-client correction accuracy.
Confidence/risk are configurable policy scores, not calibrated probabilities.
Logs/HTTP delivery are bounded best-effort queues; abrupt shutdown can lose pending
records. SQLite retention does not shrink an existing database file.

Existing test API deprecations, Mockito's class-sharing warning and Gradle's future-10
deprecation notice are non-failing on the pinned build.
