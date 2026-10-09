# Phase 12 validation — 2026-10-10 (Asia/Karachi)

Final Java 21.0.12.1 / Gradle 9.8.0 workspace command:

```sh
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home ./gradlew clean build :aegis-tools:installDist --no-build-cache --console=plain
python3 -m unittest discover -s scripts/tests -v
python3 scripts/release.py check-docs
```

**BUILD SUCCESSFUL in 1m 3s; all 32 actionable tasks executed.**

| Suite | Tests | Failures/errors/skipped |
|---|---:|---:|
| common | 364 | 0 / 0 / 0 |
| Paper | 157 | 0 / 0 / 0 |
| tools | 22 | 0 / 0 / 0 |
| Java total | 543 | 0 / 0 / 0 |
| Python candidate integrity | 4 | 0 / 0 / 0 |

The API module has no tests and is not counted. Packaging smoke verifies relocated
YAML, generation of all 24 documents, bundled SQLite and absence of server/JMH/tools
classes in the deployable jar. Existing test-API/Gradle deprecation warnings remain.

Jar: `AegisAC-0.12.0-SNAPSHOT.jar`, 12,909,203 bytes.
SHA-256: `d00573aee5e1b90f816be6416596f42ced23c6873017f7213dc51e0ea40dc63c`.
Requires standalone PacketEvents Spigot 2.14.0; compile baseline Paper API 1.21.11 / Java 21.

Exact Phase 11 defaults load byte-preserving with safe actions. Disabled categories /
custom messages, legacy migration backups/idempotence, future-schema rejection and
restart-only active-generation fences pass. Offline preflight validates bounded copies,
leaves source defaults/migrations untouched and refuses enabled global actions in
alert-only mode. The installed CLI separately preflighted the Phase 11 fixture settings.
Python integrity tests reject modified/missing/extra files, traversal, symlinks,
duplicate checksum records and false certification. Current documentation links pass.

Local candidate preparation/verification is run before publication; dirty local packages
are explicitly labelled. The CI workflow independently prepares clean-source candidates
and signs/verifies repository push provenance through GitHub OIDC/Sigstore. Actual CI
run/signature evidence is added to the draft PR after those operations complete.
Workspace checksums alone are not signed provenance.

Local logs: `/workspace/.tools/phase12-clean.log`, `phase12-preflight.log` and
`phase12-package.log`. Historical Phase 11 performance measurements are retained;
no runtime hot-loop optimization or new capacity claim was made in Phase 12.

## Pending live acceptance

The operator supplied OuiPanel server `d5579107` and stated a test server with an existing
EULA agreement is available. The workspace has no authenticated panel binding or signed-in
browser access. Requests to its console/API-shaped route return dashboard HTML, not server
state. Current Paper/PacketEvents versions and installed candidate behavior are unverified.
No jar/configuration/server state was changed remotely; no EULA was accepted by the agent.
Staged alert-only observation remains pending in OPERATIONS.md and release/status.json.
Folia/native lifecycle and other live platform/provider gates remain open. See
COMPATIBILITY.md and FOLIA.md. This is a development candidate, not a production-certified release.

[Phase 11 evidence](docs/validation/phase11.md) · [Release procedure](RELEASE.md) · [Phase 12](PHASE12.md)
