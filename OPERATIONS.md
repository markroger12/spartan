# Alert-only observation and operations

This candidate remains experimental. Begin on an isolated operator-controlled server
with an existing EULA agreement. The hosted trial target supplied by the operator is
an operator-supplied OuiPanel server; authentication and current dependency versions still need
verification. This document is a trial procedure, not a completed production observation.

## Before enabling the candidate

Verify the downloaded jar and provenance; take the stopped-server backup in UPGRADING.md.
Use the standalone PacketEvents Spigot release appropriate for the actual server version.
Keep both global action switches false in `punishments.yml` and `setbacks.yml`. Leave
`webhooks.yml: enabled: false` unless the operator separately authorizes external delivery.
Run offline `preflight ... --alert-only`. Do not delete old settings to make an upgrade pass.

On OuiPanel, open Files, preserve the old AegisAC jar/configuration, upload only the shaded
candidate jar to `plugins/`, and perform a full stop/start. PacketEvents must also be in
`plugins/`. A plugin listed during bootstrap is not necessarily enabled: require its
successful enable summary and absence of dependency/classloading errors.

Record exact Paper/Minecraft, Java, PacketEvents, AegisAC, provider and proxy versions.
Run `/ac version`, `/ac performance`, `/ac output` and `/ac profile <test-player>`.
Use `/ac edition <test-player>` to verify known Java and Bedrock identities; absent
providers remain UNKNOWN unless an explicit validated topology assertion applies.
Do not classify players by name, UUID, brand or protocol.

## Stages and acceptance evidence

| Stage | Work | Evidence required before advancing |
|---|---|---|
| Isolated smoke | Join/quit/reconnect, reload a valid setting and reject an invalid setting; inspect menus with non-op staff permissions | Successful enable, increasing packet counts, retained generation on invalid reload, zero sessions/queues after disconnect, no lifecycle exceptions |
| Legitimate mechanics | Walking/sprint/jump, ice/slime/honey, water/ladder, elytra, velocity, teleport/piston, inventory/action cases | Actual client versions, reproducible observations and uncertainty; review diagnostic false positives |
| Adverse network | Controlled high ping/jitter, lag bursts, loss and proxy/provider changes | Bounded queues/drop accounting, suppressed trust during uncertainty, no stale reconnect evidence |
| Alert-only limited population | Operator-selected players and worlds during ordinary gameplay | Duration and population recorded, diagnostic review, timing/queue/output/capture metrics, no automatic effects |
| Sustained observation | Repeat during peak load and scheduled restart/provider changes | Stable lifecycle/resource behavior; reviewed findings, rollback drill and documented unresolved cases |

Time alone does not establish acceptance. The Phase 11 synthetic 2,000-session load and
short JMH baseline are not live capacity guarantees. Keep uncertainty visible and use
server profiling to measure tick cost; AegisAC tick cadence is not MSPT. Pause advancement
for dependency/runtime errors, sustained queue drops, growing output errors, incorrect
identity, stale session evidence or unexplained legitimate findings.

Staff can subscribe to `/ac verbose` for diagnostic observations and `/ac alerts` for
eligible findings. Use `/ac inspect`, `/ac checks`, `/ac combat`, `/ac connection` and
`/ac physics` to locate the source. Diagnostics never contribute risk/VL or authorize
action. `/ac panic` blocks configured automatic punishments and, by default, setbacks
while observation/logging continues; verify status after a full restart.

For retained evidence, opt into bounded SQLite/JSONL logging, review file permissions,
retention and privacy, and monitor `/ac output`. Logs contain identifiers/world/position
metadata. Development trace recording is for one explicitly selected private test
session; keep its files private and outside source control. See PHASE11.md.

## Incident and rollback

Record exact versions, time, world, session, generation, output metrics and the relevant
stack trace. Preserve the candidate checksum/source manifest and sanitized console logs.
Turn on panic for immediate action suspension; stop the server for jar rollback.
Follow UPGRADING.md's matching jar/config/data restoration procedure. A restart clears
runtime panic and staff subscriptions, so recheck them. Never enable an unavailable
model or remove uncertainty to silence a diagnostic.

## Trial record template

- Server ID / topology / existing EULA agreement:
- Date/time/timezone / duration / population:
- Candidate version / SHA-256 / source commit / provenance result:
- Paper/Minecraft / Java / PacketEvents / proxy / providers:
- Config preflight / action switches / enabled observation categories:
- Startup and shutdown evidence / reload generation / reconnect cleanup:
- Legitimate scenario observations and diagnostic issues:
- Queue/capture/check/output timing and errors / profiler evidence:
- Provider/identity/network cases / Bedrock limitations:
- Rollback drill / unresolved cases / operator decision:

Only a completed record with actual evidence can change the live acceptance flags in
release/status.json. Native Folia remains a separate gated effort described in FOLIA.md.
