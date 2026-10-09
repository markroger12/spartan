# Verified development builds

Download [AegisAC 0.11.0-SNAPSHOT](AegisAC-0.11.0-SNAPSHOT.jar) using GitHub's
**Download raw file** button. This direct copy avoids the chat artifact download path.
It is the verified workspace build; GitHub Actions publishes independent builds with
source/run identifiers and their own checksums.

- Phase 11: 536 tests passed plus packaged configuration/SQLite/dependency isolation checks.
- Size: 12,909,200 bytes.
- SHA-256: `df980779e0cfa6f9e35b9d8de5d3c2afb21252f71faaa132f478f49f7865d893`.
- Requires standalone PacketEvents 2.14.0; compile baseline Paper API 1.21.11 / Java 21.
- Folia is disabled; this is a development snapshot, not a production-certified release.
- See [validation](../VALIDATION.md) and [trace/replay tools](../PHASE11.md).

[SHA256SUMS](SHA256SUMS) covers both retained jars. Phase 10 remains available as
`AegisAC-0.10.0-SNAPSHOT.jar`; its independent successful CI run and original workspace
validation are recorded in [the Phase 10 report](../docs/validation/phase10.md).

The GitHub Actions artifacts `AegisAC-Phase11` and `AegisAC-Phase11-tools` contain the
plugin and separate development CLI, respectively. JMH is not inside the plugin jar.
