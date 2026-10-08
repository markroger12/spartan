# Phase 7: official edition identity and translated Bedrock analysis

AegisAC 0.7.0 adds optional official Floodgate/Geyser identity queries, independent
Java/Bedrock check policies, six bounded translated observation histories and two
administrative diagnostics. It is an experimental observe-only integration; native
Bedrock physics and real-client accuracy remain unverified. Full native Bedrock
movement/combat coverage is not implemented.

## Source and ownership

The complete file list is in [PROJECT_TREE.md](PROJECT_TREE.md).

```text
aegis-api/.../player/
  EditionSnapshot.java         # immutable identity provenance
  BedrockSnapshot.java         # immutable per-lane translated history
  PlayerSnapshot.java          # coherent session API view
aegis-common/.../bedrock/
  IdentityResult.java          # positive, negative, absent, disabled, failure
  IdentityObservation.java     # configuration and provider epoch fences
  IdentityResolver.java        # conservative resolution and topology assertions
  EditionPolicy.java          # derived immutable per-session rules
  BedrockMonitor.java         # six bounded observation lanes
aegis-common/.../config/EditionSettings.java
aegis-paper/.../bedrock/
  OfficialIdentityProvider.java # public-method linkage, optional metadata
  IdentityService.java         # owner-thread round-robin queries and lifecycle
```

`IdentityService` publishes to the exact attached `PlayerData` instance, with a
provider epoch and configuration generation. The session worker derives policy
only when identity or generation changes, recreates check monitors to prevent
cross-edition evidence, and invalidates motion/world baselines on identity changes.
Snapshots hide obsolete analysis before the worker consumes another packet.
Provider queries and plugin lifecycle events stay on the owning server thread.
Folia remains unsupported.

[Bedrock contracts](BEDROCK.md) describe provider loss, negative results, optional
metadata, proxy topology, lane budgets, mode definitions and known limits. API
contracts were checked against the official public source; reflective linkage is
covered with fixtures, not a running Geyser/Floodgate server.

## Configuration and commands

The existing 24 version-1 documents remain; no new dependency is required.
`compatibility.yml` gains identity settings, `profiles/java.yml` gains all check
switches, and `profiles/bedrock.yml` gains history limits and all 72 mode rules.
Eight modes default supported, seven adjusted and 57 disabled. Existing global
and legacy per-check disables remain effective. All additions reload atomically.

`/ac edition <player>` exposes status and provenance. `/ac bedrock <player>` shows
bounded translated lane counts and uncertainty. Both have separate operator-only
permissions. `/ac checks` includes the selected catalog Bedrock mode and legacy veto.

## Build and tests

```sh
JAVA_HOME=/workspace/.tools/jdk-21 GRADLE_USER_HOME=/workspace/.tools/gradle-home \
  ./gradlew clean build --no-build-cache --console=plain
```

Deploy `aegis-paper/build/libs/AegisAC-0.7.0-SNAPSHOT.jar` with the separately installed
PacketEvents 2.14.0 plugin. Server/API baseline remains Paper 1.21.11 and Java 21;
newer server/Java versions have not been validated. Dependencies, locks and checksum
verification remain unchanged. See [VALIDATION.md](VALIDATION.md) for executed
counts, artifact hash and fixture coverage.

Tests cover official-shaped API contracts, provider precedence and failure, negative
coverage assertions, query budgets and owner-thread checks, stale/future observations,
provider epochs, reloads, reconnects, history TTL/bounds/reset, identical Java/Bedrock
rate traces, manual policy overrides and permissions. Existing Java pipeline fixtures
now explicitly publish Java identity. PacketEvents registry fixture mocks do not
retain millions of irrelevant invocation records; real buffer decoding is preserved.

No server packet is cancelled. No alert, setback, kick, ban, automated punishment,
native Bedrock simulator, Geyser instance or Floodgate instance is created. Phase 8
remains the next roadmap step; prior unavailable models and live validation remain
explicitly outstanding.
