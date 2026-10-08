# Phase 1 validation

Validated in the cloud workspace on 2026-10-05 using JDK 21.0.12.1 and Gradle 9.8.0.

`./gradlew clean build --no-build-cache` passed with dependency locks and checksum
verification enforced. All 21 tasks executed; the following 35 JUnit tests ran
with zero failures, errors or skips.

| Suite | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| dev.aegisac.common.config.ConfigurationTest | 23 | 0 | 0 | 0 |
| dev.aegisac.common.player.PlayerLifecycleTest | 5 | 0 | 0 | 0 |
| dev.aegisac.paper.BootstrapTest | 5 | 0 | 0 | 0 |
| dev.aegisac.paper.PacketEngineTest | 2 | 0 | 0 | 0 |

The API module contains no separate test sources; its contracts are exercised by
common and platform tests. NO-SOURCE tasks are not counted as tests.

The additional `:aegis-paper:verifyPluginJar` check passed. It opens the shaded jar,
verifies that Bukkit/PacketEvents and unrelocated YAML are absent, then uses an
isolated classloader containing only the jar and JDK to load the core parser and
generate all 24 YAML documents. This demonstrates the distributed parser works
without leaking dependencies from the test classpath.

A subsequent forced `:aegis-paper:shadowJar --rerun-tasks` rebuild produced a
byte-identical jar to the clean validated artifact. This is a same-machine
repeatability check, not a cross-toolchain reproducible-build certification.

Artifact: `aegis-paper/build/libs/AegisAC-0.1.0-SNAPSHOT.jar`

SHA-256: `f265dcdbf7af8ed3287625bc0000373c52669cf36389a6e6260d8904f1536f07`

The reusable cloud installation script was executed successfully after validation.
Its `install_script` and tool-activation/build `start_skill` were saved in the
environment draft. Saving is not publication or a fresh-machine restoration test.
No application service is required for the Phase 1 build/test workflow.

## What was exercised

- Fresh-file generation, preserved edits, defaults, immutable nested maps, safe
  parsing limits, semantic diagnostics and line reporting.
- Schema migration, exact backup content, renamed keys, idempotence and rejection
  before migration when another document is invalid.
- Atomic configuration publication while concurrent readers observe generations.
- Java/Bedrock/unknown profile selection independent of world difficulty.
- Join, quit, reconnect, shutdown, concurrent counters and stale-transport rejection.
- Plugin startup/shutdown and API service registration using MockBukkit.
- Staff command permissions/completion and asynchronous successful/rejected reloads.
- Actual PacketEvents adapter code with mocked transport APIs: inbound/outbound
  routing, PLAY-only filtering, cancelled-packet filtering and idempotent cleanup.
- Failed dependency initialization disables AegisAC without terminating PacketEvents.

## Explicitly not validated

No live Paper, Spigot, Purpur or Folia server was started. MockBukkit is not a
substitute for real packet injection, Minecraft clients, server plugins, regional
scheduling or a supported-version matrix. No Minecraft EULA was accepted.
The CI workflow is added but has not run on GitHub. No new cloud task was restored
from a published snapshot.

Detections, prediction/collision math, violation buffers/decay, exemptions,
punishment/setback thresholds, storage, webhooks, trace replay and gameplay
scenarios belong to later phases and are not implemented or tested here.
The current artifact must not be presented as production anti-cheat protection.

Known non-failing tooling notices: a MockBukkit test uses a deprecated Paper quit
event constructor, Mockito emits the JVM class-sharing notice, and Gradle reports
future Gradle-10 deprecations. Production code compiles without warnings under the
selected baseline, except intentionally suppressed Bukkit APIs retained for
Spigot portability.
