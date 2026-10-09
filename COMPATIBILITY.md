# Compatibility and evidence matrix

Current candidate: **0.12.0-SNAPSHOT**. No live platform/version combination has completed
release acceptance. Build support, protocol recognition and diagnostic model coverage
are separate. Consult this matrix before choosing a server jar.

| Component / combination | Evidence | Current status |
|---|---|---|
| JDK 21 / Gradle 9.8.0 | Clean compilation, JUnit, distribution checks locally and CI | Development baseline |
| Paper API 1.21.11 | Pinned timestamped API; MockBukkit 1.21 lifecycle tests | Compile/test baseline; live acceptance pending |
| PacketEvents Spigot 2.14.0 | Pinned API, adapter fixtures, hard dependency | Required separate plugin; live transport acceptance pending |
| Paper 26.2 / Java 25 | User's Phase 5 console reached a missing-packetevents error before AegisAC enabled | Unverified; the error is not evidence of runtime compatibility |
| Spigot / Purpur | Conventional platform APIs selected where possible | Unverified; no supported-version promise |
| Folia | Native scheduler/ownership fixtures; bootstrap explicitly refuses loading | Disabled; native lifecycle and independent live tests pending |
| Java client 1.8 / 1.16.5 / 1.21.11 physics profiles | Independent one-tick reference tests and synthetic candidate fixtures | Partial experimental models; not full client/version conformance |
| Other client protocols / ViaVersion / ViaBackwards | Capability recognition and conservative unknown handling | Proxy/client/version combinations require live validation |
| Floodgate / Geyser | Official optional API adapters and provider/epoch failure fixtures | Real deployment/provider-thread contracts unverified |
| Bedrock | Conservative identity policy, six translated diagnostic lanes, synthetic fixture | No native Bedrock physics or accuracy certification |
| SQLite 3.53.4.0 | Actual shaded jar opens bundled native driver in isolated smoke test | Bundled local storage; MySQL unavailable |
| Discord webhook | Fake transport, bounded queues/retry tests | Disabled by default; no real endpoint tested |

There are **72 catalog IDs: 61 implemented experimental evaluators/diagnostics and
11 unavailable models**. See CHECKS.md. Diagnostics never score or authorize effects.
Current runtime geometry/input/client acknowledgement uncertainty prevents trusted
movement/combat findings. Bedrock policy modes describe diagnostic handling, not
certified detection support.

The selected hosted trial target is OuiPanel server `d5579107`. A console-page URL
alone supplies neither authenticated API access nor current version evidence.
Record actual runtime/dependency versions in OPERATIONS.md's trial record before
claiming acceptance. Update this matrix only with identifiable test results/logs.

Configuration schema is 1. Version-0 files have supported additive migration; unknown
future versions are rejected. API record constructors remain pre-release and integrations
must be rebuilt against the matching API artifact. Release format/schema changes also
require matching tooling. Trace format 1 is development-only observational replay.
