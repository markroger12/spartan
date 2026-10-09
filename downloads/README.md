# Verified development builds

[Download AegisAC 0.12.0-SNAPSHOT](AegisAC-0.12.0-SNAPSHOT.jar) using GitHub's
**Download raw file** button. This copy is the verified workspace candidate and avoids
the failed chat download path. The CI artifact has its own source manifest and signed
provenance bundle; see [RELEASE.md](../RELEASE.md).

- 543 Java tests plus 4 candidate integrity tests passed; packaged SQLite/YAML/isolation checks passed.
- Size: 12,909,203 bytes.
- SHA-256: `d00573aee5e1b90f816be6416596f42ced23c6873017f7213dc51e0ea40dc63c`.
- Requires separate PacketEvents Spigot 2.14.0; compile baseline Paper API 1.21.11 / Java 21.
- Folia remains disabled; live hosted trial and production acceptance are pending.

[SHA256SUMS](SHA256SUMS) covers the retained Phase 10/11/12 jars. The successful Actions
candidate artifact **AegisAC-Phase12** includes the plugin, tools ZIP, defaults, docs,
source/status manifest and checksums. Repository push runs additionally publish
**AegisAC-Phase12-provenance** after signing and verification. Verify the actual signature;
a checksum or unsigned workspace copy alone does not certify the publisher.
