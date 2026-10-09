# Phase 11 — development replay and measurement

Version `0.11.0-SNAPSHOT` adds development trace recording, offline replay, a synthetic
scenario corpus, constant-space analysis metrics, JMH and a session load runner.
These tools do not establish production detection accuracy or live player capacity.
Phase 10's native lifecycle/live acceptance gates remain open and Folia stays disabled.

## Record one development session

Recording is **off by default**. On a private development server, add this JVM option
**before** `-jar` and restart:

```
-Daegisac.development.trace-player=00000000-0000-0000-0000-000000000000
```

Replace the value with the test player's UUID. Only the first matching connection
in that plugin lifetime is selected. Remove the option to disable recording.
There is no wildcard, automatic production recording, or background upload.
The option deliberately lives outside normal tuning configuration: it requires a restart.

Files appear in `plugins/AegisAC/development-traces/`. Limits are fixed:

| Resource | Limit |
|---|---:|
| Selected sessions per plugin lifetime | 1 |
| Trace lifetime | 60 seconds, including idle time |
| Writer queue | 32 immutable observations |
| Records per file | 2,000 |
| File size | 16 MiB |
| Encoded observation | 1 MiB |
| Strings / collection elements / nesting | 4 KiB / 4,096 / 24 |
| Retained directory | 8 files, at most 128 MiB for recorder-created files |

Offers never perform disk I/O, serialization or reflection on the packet worker.
A single daemon writes the bounded queue. Fingerprinting happens once when the trace
is attached. Reload stops recording rather than silently mixing analysis settings.
Disconnect/disable requests draining; it does not block the server on disk. A process
exit before the footer is written leaves an incomplete file that replay rejects.
Limits stop capture; a full directory is reported without deleting existing files.
Files are not rotated or silently overwritten. Archive/delete development files deliberately.

Captured inputs include normalized movement, impulses, timing acknowledgements,
interactions and other normalized variants; processing/observation clocks, packet/loss
sequence, entity ID, identity/provider generation, owner policy, tick health, collision
snapshot and an analysis-settings fingerprint. Existing immutable geometry bounds still
apply. No raw packet buffers, item NBT, configuration documents, webhook secrets, player
name or session UUID are written. **World names, coordinates, client brands and tracked
entity UUIDs remain sensitive.** Keep traces private and never commit real recordings.

## Replay offline

Download `AegisAC-Phase11-tools` from a successful GitHub Actions run, or build the
development distribution with Java 21:

```sh
./gradlew :aegis-tools:installDist
./aegis-tools/build/install/aegis-tools/bin/aegis-tools replay recording.aegistrace /path/to/config-copy
```

Use a **copy** of the original configuration directory: the existing loader fills missing
default files and performs supported migrations. Its analysis fingerprint must match.
Output-only settings/credentials are excluded from the fingerprint and never executed.
The tools module depends only on the common/API layers; it cannot dispatch Bukkit
commands, webhooks, storage writes or setbacks. Output includes diagnostic counts by check, aggregate findings, maximum observed transaction
RTT/loss epoch, physics uncertainty and explicit replay limitations.

The format has a magic/version header, fixed DTO allowlist, bounded lengths, strict
sequence ordering, SHA-256 digest and mandatory completion footer. There is no Java
object deserialization or class loading from a file. Corruption, truncated files,
unknown types/versions, recorded drops, sequence gaps and mismatched analysis settings
fail explicitly. Nonfinite *packet* values remain available for invalid-value checks;
world geometry still passes its normal constructor validation. The checksum detects
accidental corruption; it is not a signature from a trusted server.

This is **observational replay**, not bit-for-bit reproduction of an asynchronous live
server. Entry observations may race owner publication during analysis. Capturing after
sequence 1 starts with missing prior history, and the report says so. Manual freeze/
exemption history is not serialized: traces containing it are rejected. Resource-limit
prefixes are labelled. Reports cannot authorize enforcement or certify a false positive.
Use a complete fresh connection and stable captures to narrow a reproduction.

## Fixture corpus

```sh
./aegis-tools/build/install/aegis-tools/bin/aegis-tools corpus /tmp/aegis-fixtures /tmp/aegis-config
./aegis-tools/build/install/aegis-tools/bin/aegis-tools replay /tmp/aegis-fixtures/legitimate/walking.aegistrace /tmp/aegis-config
```

`FixtureCorpus` is the versioned, executable fixture source. Generation writes new files
only. Legitimate cases: walking, sprinting, jumping, ice, slime, honey, water, ladder,
elytra, velocity, high ping, lag spike with loss, teleport, piston and Bedrock translation.
Suspicious cases are separate: speed bursts and nonfinite coordinates. Each contains
80 inputs. These are synthetic observations, **not independent vanilla captures**.
Existing independent numeric physics reference tests remain necessary. Corpus tests
check repeatable disk replay, meaningful suspicious diagnostics and retained uncertainty;
zero trusted findings alone is not evidence of complete detection accuracy.

## Performance metrics

`/ac performance` retains packet counts, queue depth/drops, decode failures, capture
counts and active players, and adds:

- `check_evaluations`: applicable enabled evaluations accepted by movement/combat/guard
  dispatchers, including gated diagnostic evaluations. It is cumulative across resets.
- `movement_eval_us`: mean time around the pure movement geometry evaluators, including
  calls that return not-applicable; no permission gates or dispatch/output time.
- `analysis_batches` / `analysis_us`: packet batches and mean guard + state/physics +
  movement + combat processing time. This is **not** mean time per individual check.
- `debug_dropped`: development records rejected by queue/file limits or discarded on failure.

Timings include clock overhead. Disabled recording retains no trace queue/thread; normal
analysis metrics use constant-space counters. An existing customized `performance`
message is preserved by configuration merge; add the new `%...%` placeholders to display
these metrics on upgraded servers.

## JMH and load tests

```sh
./gradlew :aegis-tools:benchmark '-PjmhArgs=.*Benchmark -wi 2 -i 3 -w 1s -r 1s -f 2 -prof gc -rf json -rff /tmp/aegis-jmh.json'
./aegis-tools/build/install/aegis-tools/bin/aegis-tools load 2000 100
```

JMH 1.37 measures throughput, sampled latency (including percentiles), and allocation/GC
for walking/water/piston physics steps and SpeedA/AccelerationA evaluation. Two fresh
forks, warmup and measured iterations are used in the recorded baseline. Each fork has
256 MiB heap. Results consume returned values; no production hot-loop optimization is
claimed from these short synthetic runs. Increase iterations/forks and use representative
server hardware before changing algorithms or publishing capacity claims.

The load runner creates real registries, bounded serial queues, packet workers and
configured analysis sessions, submits normalized movement, waits for accounting to
complete, then asserts queue/session cleanup. It reports processed/dropped/failures
separately. It omits network decoding, owner geometry captures, persistence and Bukkit
work. A high session count is a queue/lifecycle stress result, not a supported live
population. Inputs are capped at 5,000 sessions and 200 packets per session.

JMH and its transitive dependencies are confined to `aegis-tools`; none is shaded into
the plugin jar. Dependency locks and checked SHA-256 pins remain enforced.
