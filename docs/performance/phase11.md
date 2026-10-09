# Phase 11 synthetic performance baseline

Measured 2026-10-09 (Asia/Karachi), Java HotSpot 21.0.12.1+1-LTS-4, Linux x86_64,
virtual Intel Xeon Platinum 8573C, five visible CPUs / four CPU quota, 32 GiB cgroup
memory limit. This shared virtual machine is not a controlled production server.
Each JMH fork used `-Xms256m -Xmx256m`, one benchmark thread, two forks, two 1-second
warmups and three 1-second measured iterations per fork. JMH version 1.37, GC profiler.
No other build/test workload was deliberately run concurrently with these benchmarks.

| Workload | Throughput (million ops/s) | Sample mean (µs/op) | Sample p99 (µs/op) | Throughput allocation (B/op) |
|---|---:|---:|---:|---:|
| walking | 2.30 | 1.330 | 3.432 | 1136 |
| water | 1.41 | 2.945 | 38.470 | 1472 |
| piston | 1.20 | 3.937 | 38.947 | 1568 |
| SpeedA | 59.96 | 0.199 | 0.092 | 40 |
| AccelerationA | 58.25 | 0.514 | 0.108 | 40 |

Throughput and sample columns come from separate JMH modes, not reciprocal conversions.
Long pauses in this shared environment make the sampled means and very high percentiles
noisy; some means exceed p99 because of rare extreme pauses. Confidence intervals,
per-fork samples, GC measurements and full distributions are preserved in
[the gzip-compressed JMH JSON](phase11-jmh.json.gz). Use `gzip -dc` to inspect it.
Piston sample-mode allocation was about 1,665 B/op versus 1,568 B/op in throughput mode;
JIT/profiling differences matter. This is an initial baseline, not a regression threshold
or evidence of a production latency SLO. No hot-loop optimization is claimed.

Physics is one step with a small captured world; a real packet can explore many
candidates and run multiple checks. Check benchmarks return an evaluation to JMH.
Neither includes networking, owner capture, database/webhook work or JVM server ticks.

The separate [load result](phase11-load.txt) processed 200,000 packets across 2,000
registered sessions with two packet workers, zero recorded drops/failures, and zero
remaining queues after detach, in about 2.40 seconds including attach/detach.
The JVM heap was capped at 1 GiB. Missing owner/world observations deliberately keep
this queue/lifecycle workload uncertain. This does not establish 2,000-player capacity.

Reproduce:

```sh
./gradlew :aegis-tools:benchmark '-PjmhArgs=.*Benchmark -wi 2 -i 3 -w 1s -r 1s -f 2 -prof gc -rf json -rff /tmp/aegis-jmh.json'
./gradlew :aegis-tools:installDist
JAVA_OPTS=-Xmx1g ./aegis-tools/build/install/aegis-tools/bin/aegis-tools load 2000 100
```

See [PHASE11.md](../../PHASE11.md) for the fixture corpus and measurement boundaries.
