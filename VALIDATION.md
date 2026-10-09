# Phase 11 validation — 2026-10-09 (Asia/Karachi)

The final workspace build of `0.11.0-SNAPSHOT` passed with Java 21.0.12.1 and the
pinned Gradle 9.8.0 wrapper:

```sh
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home ./gradlew clean build :aegis-tools:installDist --no-build-cache --console=plain
```

**BUILD SUCCESSFUL in 1m 18s; all 31 actionable tasks executed.**

| Suite | Tests | Failures/errors/skipped |
|---|---:|---:|
| common | 360 | 0 / 0 / 0 |
| Paper | 157 | 0 / 0 / 0 |
| development tools | 19 | 0 / 0 / 0 |
| Total | 536 | 0 / 0 / 0 |

The API module has no test sources and is not counted as a passing test suite.
The jar smoke test loaded the actual distribution with only a JDK parent, generated
all 24 configuration documents, opened bundled SQLite, checked relocated YAML, and
rejected JMH/tools/server classes leaking into the plugin. Existing deprecation warnings
remain in two test APIs and Gradle's pre-10 compatibility report; they are not failures.

Final plugin jar: `aegis-paper/build/libs/AegisAC-0.11.0-SNAPSHOT.jar`

- Size: 12,909,200 bytes.
- SHA-256: `df980779e0cfa6f9e35b9d8de5d3c2afb21252f71faaa132f478f49f7865d893`.
- Runtime dependency: standalone PacketEvents 2.14.0.
- Compile baseline: Paper API 1.21.11 / Java 21.

## Replay and measurement evidence

All 24 normalized packet variants round-trip, including nonfinite diagnostic input.
Tests reject damaged/truncated/unknown/oversized trace data, invalid ordering and
mismatched analysis configuration. Record/directory budgets, async draining and
reload/session recording fences are exercised. Applicable evaluation counters survive
monitor resets. The corpus runs 15 legitimate synthetic cases and two suspicious cases
through disk serialization and isolated replay; special mechanics retain uncertainty.
High-ping replay verifies a 600 ms transaction RTT; the lag fixture preserves loss epoch.
A 256-session/16,384-packet cleanup load is part of the test suite.

The installed CLI separately generated the corpus and replayed walking/high-ping traces.
Walking produced two **diagnostics**, zero trusted findings and explicit inferred-input /
unacknowledged-world uncertainty. This is not a zero-diagnostic false-positive claim.
The larger load processed 200,000 normalized packets across 2,000 sessions with zero
drops/failures and no retained queues after cleanup, in about 2.40 seconds. It omits
network decoding, owner captures and server work and does not establish live capacity.

[JMH results and raw measurements](docs/performance/phase11.md) cover throughput,
sampled latency and allocation for three physics workloads and two evaluators with
two forks and warmup. Shared-host tails are noisy; no production SLO or optimization
claim follows from these short measurements.

Local logs: `/workspace/.tools/phase11-final.log`, `phase11-jmh.log`, `phase11-load.log`
and `phase11-cli-final.log`. These paths are workspace evidence, not portable downloads.
The GitHub workflow independently rebuilds/tests and uploads the plugin and tools.

## Remaining acceptance gates

Replay is observational; it cannot reproduce all asynchronous owner publications or
missing prior history. Manual exemption/freeze recordings are rejected. Real-client
trace conformance and independent live Paper/Folia acceptance were **not run**.
Folia remains disabled; Phase 10 native lifecycle and service gates in [FOLIA.md](FOLIA.md)
remain open. Paper 26.2 / Java 25 runtime compatibility is not certified. Punishment /
setback safeguards and unavailable models remain unchanged.

Usage and limits: [PHASE11.md](PHASE11.md). Previous evidence:
[Phase 10](docs/validation/phase10.md), [Phase 9](docs/validation/phase9.md).
