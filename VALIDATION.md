# Phase 10 ownership milestone validation

Validated on 2026-10-08 (UTC), JDK 21 and Gradle 9.8.0.
**This validates the scheduling and service ownership milestone, not complete Folia runtime support.**
Historical Phase 1–9 reports are retained under `docs/validation/`.

## Executed build

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 1m 22s; all 21 actionable tasks executed.**
**510 tests passed; 0 failures, 0 errors, 0 skipped.**
Common: 353; Paper: 157. API has no test sources;
NO-SOURCE is not counted as testing. Clean removed prior results, and build-cache
reuse was disabled. The results below supersede intermediate runs.

Log: `/workspace/.tools/aegis-phase10-verified.log`. XML/HTML reports are in each
module's `build/test-results/test` and `build/reports/tests/test`.

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
| BootstrapTest | 13 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 16 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| AdminMenuTest | 25 | 0 | 0 | 0 |
| ScheduledAdminTest | 3 | 0 | 0 | 0 |
| IdentityServiceTest | 8 | 0 | 0 | 0 |
| OfficialIdentityProviderTest | 5 | 0 | 0 | 0 |
| AsyncLogStoreTest | 4 | 0 | 0 | 0 |
| OutputServiceTest | 14 | 0 | 0 | 0 |
| ScheduledOutputTest | 8 | 0 | 0 | 0 |
| VerifiedSetbackTest | 8 | 0 | 0 | 0 |
| WebhookWorkerTest | 4 | 0 | 0 | 0 |
| PlayerDirectoryTest | 4 | 0 | 0 | 0 |
| BukkitPlatformSchedulerTest | 7 | 0 | 0 | 0 |
| FoliaPlatformSchedulerTest | 8 | 0 | 0 | 0 |
| ManagedTasksTest | 6 | 0 | 0 | 0 |
| ScheduledCaptureTest | 4 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 3 | 0 | 0 | 0 |
| WorldCaptureTest | 11 | 0 | 0 | 0 |

## Packaged artifact

- File: `aegis-paper/build/libs/AegisAC-0.10.0-SNAPSHOT.jar`
- Size: 12880395 bytes
- SHA-256: `861cb3807662d0e6eb91fdb8c923f80b912b1b863e43c02441e3a33a1a5ddd83`

The packaged-jar verification loaded the actual shaded artifact with only a JDK
parent, generated all 24 YAML documents and opened SQLite with the bundled driver.
Server/PacketEvents classes remain external. No dependency, lock or verification
metadata changes were needed for this milestone. Conventional Paper is the selected
runtime adapter; Folia bootstrap remains explicitly rejected.

## New evidence

- Independent Bukkit and native-Folia API fixture suites verify routing to global,
  entity, region and async schedulers, tick/millisecond units, recurring tasks,
  cancellation, invalid arguments, ownership denial and no Bukkit fallback on Folia.
- Managed tasks enforce capacity and close admission, handle cancellation/completion
  before native-handle publication, execute retirement once, release failed tasks,
  and prevent overlapping callbacks from executing the same action concurrently.
- SessionAudience sends player/console replies on their owner and rejects replaced
  sessions; non-player senders cannot be silently elevated into console audiences.
- The capture coordinator performs no player reads on the global path, limits one
  pending capture per session, retires old sessions, invalidates rejected captures,
  and cannot publish queued work after shutdown. Global Folia cadence stays unknown.
- Unowned capture areas cause no block/chunk/nearby-entity reads. Individual foreign
  entity bounds are skipped, and skipped entries still count toward the iteration cap.
- Setback destination ownership is checked before geometry scans. Native-Folia
  ownership never falls back to synchronous teleport.
- Bootstrap rejection is tested before conventional scheduler creation, packet
  listener registration or API registration. All existing regression suites pass.

Additional service fixtures verify immutable directory reads, session replacement,
recipient-owner permission checks and delivery, remote freeze expiry, old-request
retirement, data-only close, provider invalidation during a query, native async
teleport without blocking, and expiring source grants before global command dispatch.
The final build includes all 19 additional service tests since the scheduler-only
milestone. Intermediate fixture failures were corrected: an incorrect identity reason
name and Mockito restubbing that accidentally invoked an ownership assertion from the
test coordinator. The assertions still enforce the same ownership/freshness contracts.
No tests were disabled or skipped to obtain this result.

## Unrun gates and limitations

No live Paper or Folia server was run. Native adapter fixtures are not independent
live-runtime certification. The workspace contains no configured live servers or
existing EULA files; a test-server availability question remains unanswered. No
EULA was accepted, external notification sent or remote server changed.

Native lifecycle/inventory teardown and actual dependency/command integration remain
release gates in FOLIA.md. **Full Phase 10 is unfinished, and Folia remains disabled.**
The service migrations, region-local health, native async correction and global command
handoffs have fixture coverage; they have not been exercised on a live native backend.
The plugin still compiles against Paper API 1.21.11 on Java 21; Paper 26.2 on Java 25
and live provider/client combinations remain unverified. Standalone PacketEvents is
still required.

Diagnostic evidence never scores or authorizes punishment. Automatic punishment
and setbacks default off, and runtime speculative physics cannot produce verified
safe positions. Existing unavailable checks, native Bedrock physics and MySQL remain
separate work. Cancellation cannot undo an already executing callback; callers retain
session, generation and evidence fences. Existing non-failing test API deprecations,
JVM class-sharing warnings and Gradle future-version notices remain on the pinned build.
