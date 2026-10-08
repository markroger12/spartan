# Phase 3 validation

Validated on 2026-10-06 with the prepared JDK 21 toolchain and Gradle 9.8.0.
Phase 1/2 historical reports are retained in `docs/validation/`.

## Executed validation

```sh
cd /workspace/spartan
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 10s; all 21 actionable tasks executed.**
The clean build removed previous results; no test results came from build cache.
**155 tests passed; 0 failures, 0 errors, 0 skipped.**
The API module has no test sources; its NO-SOURCE task is not counted as tests.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| CollisionSolverTest | 9 | 0 | 0 | 0 |
| ConfigurationTest | 23 | 0 | 0 | 0 |
| PhysicsConfigurationTest | 9 | 0 | 0 | 0 |
| PipelineConfigurationTest | 12 | 0 | 0 | 0 |
| ConnectionTrackerTest | 5 | 0 | 0 | 0 |
| TransactionTrackerTest | 7 | 0 | 0 | 0 |
| BrandDecoderTest | 1 | 0 | 0 | 0 |
| SerialPacketQueueTest | 5 | 0 | 0 | 0 |
| PhysicsReplayTest | 28 | 0 | 0 | 0 |
| PacketStateTest | 8 | 0 | 0 | 0 |
| PhysicsPipelineTest | 5 | 0 | 0 | 0 |
| PlayerLifecycleTest | 5 | 0 | 0 | 0 |
| WorldViewTest | 2 | 0 | 0 | 0 |
| BootstrapTest | 7 | 0 | 0 | 0 |
| PacketEngineTest | 2 | 0 | 0 | 0 |
| PacketNormalizerTest | 12 | 0 | 0 | 0 |
| PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |
| WorldCaptureServiceTest | 2 | 0 | 0 | 0 |
| WorldCaptureTest | 9 | 0 | 0 | 0 |

Reports are in each module's `build/test-results/test` and `build/reports/tests/test`.
The execution log in this workspace is `/tmp/aegis-phase3-clean.log`.

## What the tests demonstrate

Phase 3 adds collision clipping and stepping tests, independently calculated
ordinary-motion references, a full synthetic jump arc, friction, effects, velocity
and explosion-vector simulation, plus explicit uncertainty for unsupported
mechanics. Tests cover exact protocol IDs from the installed PacketEvents API,
owner-thread guards, negative coordinate conversion, shape pieces, unloaded chunks,
cell/shape/entity budgets, round-robin capture and failure handling, immutable
snapshots, CAS invalidation/closure, stale/future snapshots, bursts, teleports,
disconnect, permission-gated physics diagnostics and atomic config preservation.
The earlier packet, configuration, lifecycle and real Netty-buffer decoder tests
also run. These are synthetic fixtures and mocked server APIs, not live traces.

`verifyPluginJar` loads the actual shaded jar with only JDK parent classes and
creates all 24 YAML documents. It verifies relocation and absence of bundled
Bukkit, PacketEvents, Netty and unrelocated SnakeYAML. A separate archive inspection
confirmed the physics engine, collision solver, owner-thread adapter, API record
and physics defaults are present. `git diff --check` passed.

## Distribution

Artifact: `aegis-paper/build/libs/AegisAC-0.3.0-SNAPSHOT.jar`

Size: **537,651 bytes**

SHA-256: `7cb9cf7c3b158c43c4db27f32df7978c7a27b821513e91a96f0cd4c1290449e4`

Production and test dependencies, locks and verification metadata are unchanged
from Phase 2. No TLS or checksum verification was disabled. Offline resolution
could not resolve Paper's timestamped snapshot metadata; normal online resolution
and the verified clean build succeeded. An early test expectation for chunk data
was updated for intentional invalidation markers. A hand-transcribed motion
reference was corrected using independent high-precision decimal calculation,
and the modern ground-acceleration coefficient is tested separately from legacy.

Non-failing diagnostics: two existing deprecated API calls in tests, Mockito JVM
class-sharing warnings, and Gradle notices about future Gradle 10 compatibility.
The pinned Gradle version is 9.8.0.

## Not validated / not claimed

No live Paper, Spigot, Purpur, proxy/ViaVersion/Geyser or Folia run; no real-client
trace corpus, full physics conformance, adversarial client trial, large-server
benchmark or published GitHub Actions run. Folia is deliberately rejected.
Unsupported mechanics produce uncertainty. Runtime candidates use unacknowledged
server geometry and inferred inputs, and are never valid enforcement thresholds.
There are zero checks, violations, setbacks or punishments. See `PHASE3.md`.
