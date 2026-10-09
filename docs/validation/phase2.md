# Phase 2 validation

The latest validation ran in the cloud workspace on 2026-10-05 with JDK 21.0.12.1
and Gradle 9.8.0. Historical Phase 1 results are preserved in
[docs/validation/phase1.md](docs/validation/phase1.md).

`./gradlew clean build --no-build-cache` passed with dependency locks and SHA-256
verification enforced: all 21 actionable tasks executed. **90 JUnit tests ran,
with zero failures, errors or skips.**

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| dev.aegisac.common.config.ConfigurationTest | 23 | 0 | 0 | 0 |
| dev.aegisac.common.config.PipelineConfigurationTest | 12 | 0 | 0 | 0 |
| dev.aegisac.common.connection.ConnectionTrackerTest | 5 | 0 | 0 | 0 |
| dev.aegisac.common.connection.TransactionTrackerTest | 7 | 0 | 0 | 0 |
| dev.aegisac.common.packet.BrandDecoderTest | 1 | 0 | 0 | 0 |
| dev.aegisac.common.packet.SerialPacketQueueTest | 5 | 0 | 0 | 0 |
| dev.aegisac.common.player.PacketStateTest | 8 | 0 | 0 | 0 |
| dev.aegisac.common.player.PlayerLifecycleTest | 5 | 0 | 0 | 0 |
| dev.aegisac.paper.BootstrapTest | 6 | 0 | 0 | 0 |
| dev.aegisac.paper.PacketEngineTest | 2 | 0 | 0 | 0 |
| dev.aegisac.paper.PacketNormalizerTest | 12 | 0 | 0 | 0 |
| dev.aegisac.paper.PacketPipelineIntegrationTest | 4 | 0 | 0 | 0 |

The API module has no separate test classes; its contracts are exercised by core
and platform suites. NO-SOURCE and up-to-date tasks are not counted as new tests.

## Evidence

- Queue tests exercise real concurrent producers/workers, serial ordering, bounded
  overflow, fair batches, executor rejection without caller-thread analysis,
  processing failure recovery and close behavior.
- Timing tests cover exact stream/window matching, duplicate/reused/unknown tokens,
  ordering, timeouts, capacity eviction, signed IDs and nanoTime wrap, separate RTT
  streams, jitter, stalled connections and backlog-safe observation timestamps.
- Player tests verify partial movement, non-finite input uncertainty, teleport
  confirmation and full relative-mask retention, inventory authority, local-only
  velocity timestamps, bounded history, world resets and late closed-session work.
- Configuration tests cover prior Phase 1 files inheriting defaults, bounded
  settings, NaN/Infinity rejection and whole-generation preservation when a
  startup-only pipeline change is rejected.
- Platform tests retain MockBukkit lifecycle, permissions and atomic reload tests,
  and add `/ac connection`, literal untrusted brand text, decoder failure recovery,
  disabled/legacy/unknown probe policy, shutdown and unknown-client behavior.
- Packet fixtures use **actual PacketEvents wrappers and Netty byte buffers**.
  They decode representative 1.8, 1.16.4/1.16.5-protocol and 1.21.11 movement,
  signed pong/window IDs and long keep-alive IDs. They verify cached-wrapper
  mutations, buffer release independence, truncation/size limits, teleport masks,
  inventory/input/velocity values, bounded brand decoding and version capabilities.

The distribution smoke check passed in the clean build. It loads the shaded jar
with an isolated JDK-only parent classloader, then instantiates the actual parser
and generates all 24 YAML documents. After strengthening its dependency-exclusion
assertion, `./gradlew :aegis-paper:verifyPluginJar` passed again and confirmed that
Bukkit, PacketEvents, Netty and unrelocated YAML classes are not bundled.

Artifact: `aegis-paper/build/libs/AegisAC-0.2.0-SNAPSHOT.jar`

SHA-256: `46e16e661f4bd7ee67952ba325f1743936dffcb0d2313d4ea0b14f891bde853a`

## Limits

No live Paper/Spigot/Purpur/Folia server or real Minecraft client/proxy was run.
No Minecraft EULA was accepted. Decoder fixtures are representative regression
tests, not a complete protocol compatibility certification or live injection
test. Optional probes default off pending operator validation of the deployment.

This phase does not validate cheat detection, physics/collision, verified safe
positions, lag-compensated reach, punishment/setbacks, SQLite, webhooks, replay
traces, server TPS/MSPT sampling or Folia. Those implementations remain scheduled
in the architecture. RTT is measured at callbacks, not confirmed wire writes,
and no world-delivery fence is exposed. No throughput/allocation benchmark or
production false-positive claim is made.

GitHub CI was updated but not executed remotely. Cloud startup instructions were
updated as a draft; no publication or fresh-task restoration is claimed.

Non-failing tooling notices remain: tests use two deprecated compatibility APIs,
Mockito emits a JVM class-sharing notice, and Gradle reports future Gradle-10
deprecations. Production sources compile without warnings under the pinned API.
