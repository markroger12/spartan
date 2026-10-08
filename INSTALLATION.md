# Installation and development

1. Install a JDK 21 and set `JAVA_HOME` to it. Confirm `java -version` and
   `javac -version` both report 21. The wrapper downloads and verifies Gradle.
2. From the checkout root run `./gradlew build`. On Windows use `gradlew.bat build`.
3. On an isolated Paper 1.21.11 test server, install the official PacketEvents
   2.14.0 Spigot release and `aegis-paper/build/libs/AegisAC-0.10.0-SNAPSHOT.jar`
   in `plugins/`. Spigot/Purpur and other versions need separate validation.
4. Start the server using its documented instructions and your own Minecraft EULA
   agreement. This project does not accept the EULA or start a public server.
5. Confirm AegisAC enabled, `movement-evaluators=15`, `experimental=true`, `diagnostics-never-punish=true`, configuration
   generation succeeded, and no dependency errors occurred. Connect a test client
   and use `/ac profile <name>` and `/ac performance` to verify packet counters. `/ac connection <name>` shows
   timing and loss; -1 RTT means no valid sample yet, not zero latency.
   `/ac physics <name>` shows experimental candidates and uncertainty; it is not a check.
6. Change `default-profile` to `strict` and run `/ac reload`. Confirm generation
   increments. Try an invalid profile; confirm rejection and unchanged generation.
7. Disconnect the client: active sessions must return to zero. Stop the server and
   confirm listener cleanup. Live processes must be started again on new machines.

The application does not require a database, webhook, API key or secret for detection or staff administration in Phase 9.
Do not install both the shaded and unbundled AegisAC artifacts. Avoid server `/reload`
commands and third-party plugin hot unloaders; use `/ac reload` for settings and a
full server restart for jars. API classloading and PacketEvents injection are not
reconstructed by a settings reload.

## Development commands

```sh
./gradlew build                       # compile, tests, isolated distribution smoke check
./gradlew :aegis-common:test          # configuration, collision, physics and packet core
./gradlew :aegis-paper:test           # MockBukkit lifecycle and PacketEvents adapter
./gradlew :aegis-paper:shadowJar      # build distribution without claiming tests ran
```

The root `build` runs all modules. Test XML and HTML reports live in each module's
`build/test-results/test/` and `build/reports/tests/test/` directories. Dependencies
are locked, and `gradle/verification-metadata.xml` pins their SHA-256 checksums.
Review lock and checksum changes against official publications when upgrading.
Never disable TLS or dependency verification to work around a download error.

Each Codex cloud task already has an isolated checkout: use `/workspace/spartan`;
do not create a Git worktree unless explicitly requested. This prepared instance
has a verified JDK at `/workspace/.tools/jdk-21` and Gradle cache at
`/workspace/.tools/gradle-home`:

```sh
export JAVA_HOME=/workspace/.tools/jdk-21
export GRADLE_USER_HOME=/workspace/.tools/gradle-home
export PATH="$JAVA_HOME/bin:$PATH"
cd /workspace/spartan
./gradlew build
```

The machine-local Gradle properties use the platform proxy and system Java trust
store. Those settings are intentionally outside the repository and contain no
credentials. Other machines should use their own supported trust/proxy settings.


Phase 3 optional ping probes default off. Validate an isolated server/proxy/client
combination before enabling `performance.yml: pipeline.active-probes` and restarting.
There is no extra credential or service dependency for packet workers. Resource
settings require a restart; ordinary profile/messages/metrics reloads still work.

Phase 3 world capture runs on the non-Folia server thread. `performance.yml: physics`
sets restart-bound capture budgets and snapshot lifetime. Check `/ac performance`
for capture failures and [PHASE3.md](PHASE3.md) for model limits. This phase needs
no additional plugin, service or credential.

Phase 4 adds `/ac checks`, `/ac movement <name>` and `/ac safeposition <name>`.
Existing `checks/movement.yml` files that say `enabled: false` stay disabled; set
that flag explicitly and `/ac reload` when you want experimental observation.
New installations enable the 15 implemented evaluators. No configuration can
activate the three missing models or enforcement. `exemptions.yml` controls
explicit world/permission/gamemode exemptions; bypass is not granted to operators
by default. Read [PHASE4.md](PHASE4.md) before interpreting evidence.

Phase 5 additionally exposes `/ac combat <name>` and `/ac cps <name>`. Fresh combat
configuration enables diagnostics; an upgraded `checks/combat.yml` with
`enabled: false` remains disabled. Startup reports `combat-evaluators=15` and no
enforcement. Confirm permissions, generation changes, target removal, swing timing
and loss handling on an isolated live server before assessing accuracy. No live
client certification is implied by the packaged fixture tests.

Phase 6 adds `/ac inspect <name> [category]`, protected by `aegisac.inspect`. Fresh
world/player/inventory/protocol/exploit files enable implemented diagnostics; old
`enabled: false` files remain disabled. `/ac checks` exposes unavailable prerequisites
and startup reports `guard-evaluators=31; guard-unavailable=8`.

PacketEvents is a **separate required plugin**, not bundled inside AegisAC. Put the
official PacketEvents Spigot distribution and the AegisAC shaded jar in `plugins/`
and perform a full restart. An `UnknownDependencyException: [packetevents]` means
Paper rejected AegisAC before its enable code ran. A plugin appearing in the bootstrap
list does not establish successful enablement. The supplied Paper 26.2/Java 25 log
shows this missing dependency; the pinned Paper 1.21.11/PacketEvents 2.14.0 API build
does not certify that newer runtime. Verify the chosen PacketEvents release supports
your server version before live testing. See TROUBLESHOOTING.md for unrelated errors.

## Phase 7 identity setup

Install the official Floodgate/Geyser plugins appropriate for the actual backend
or proxy topology. They remain optional; PacketEvents remains required. The default
identity policy is conservative: missing or non-positive provider results are
UNKNOWN, and Java movement/combat checks are disabled by the edition profile.
For a verified Java-only deployment, set `identity.java-only-server: true` in
`compatibility.yml`. For complete local provider coverage, use the separate
`negative-results-authoritative` assertion and explicitly disable unused providers.
Do not assert coverage based only on plugin presence. See [BEDROCK.md](BEDROCK.md).

After restart/reload and provider polling, inspect `/ac edition <name>` and
`/ac bedrock <name>`. Confirm source and edition using known Java and Bedrock clients.
Exercise reconnect, provider disable, reload and delayed observations on an isolated
test server. Confirmed Bedrock physics must show `BEDROCK_PHYSICS_UNAVAILABLE`.
No live provider/client matrix has been certified by the development tests.

## Phase 8 observation rollout

Start with punishments and setbacks disabled (the defaults). Staff with permissions
can subscribe with `/ac verbose` for diagnostic observations; `/ac alerts` is for
eligible findings. Use `/ac output` to monitor backpressure and I/O errors. An absence
of eligible findings is expected while models retain uncertainty.

To retain evidence, enable `logging.yml: enabled` and `storage.yml: enabled` for
SQLite. No separate database server or driver download is required. Optionally enable
JSONL. Data is written under `plugins/AegisAC/logs/`; review retention and file access.
`/ac logs <player-or-uuid>` queries up to ten SQLite records asynchronously. Configure
a private official Discord webhook only when external delivery is desired; all
external delivery defaults off and was not exercised against a live endpoint here.

`/ac panic` immediately blocks automatic punishments and, by default, setbacks while
keeping observation and logging active. Recheck its status after a full restart.
Do not assume that enabling a rule makes diagnostic evidence eligible. Read PHASE8.md
before configuring commands or corrections. The current runtime does not produce
verified safe positions. No live ban plugin or correction accuracy was validated.

## Phase 9 staff rollout

Use `/ac gui` after upgrading. Old `gui.yml` files with `enabled: false` are preserved;
set `enabled: true` and `/ac reload` to opt in. Grant granular permissions from
[PHASE9.md](PHASE9.md), and test with a non-operator staff account. Left-click checks
to toggle observation, right-click to toggle per-check punishment policy. Global
punishment remains separately controlled and defaults off. Settings edits save an
adjacent `.last-admin-edit.bak`; reopen stale menus after reload or rejected edits.

Additional `config.yml: command-aliases` require restart. Test check toggles, default
and per-world profile selection, disconnect/reconnect, reset, timed exemptions,
freeze/unfreeze and shift/number-key/drag interactions on an isolated client/server.
The development tests exercise MockBukkit; they do not replace that live acceptance
check. Difficulty profiles remain labels, not threshold multipliers.

## Phase 10 ownership milestone

The 0.10.0 snapshot runs the conventional Paper adapter and keeps Folia blocked.
Do not add `folia-supported` or remove the bootstrap rejection. Native scheduler
fixtures do not certify runtime behavior. See FOLIA.md for native lifecycle gates
and the independent live test protocol. Region-aware world capture adds one scheduler
handoff; capture freshness/revision checks still apply, and queue rejection invalidates
geometry. No additional dependency, service or credential is needed for the build.
