# Verified workspace build

This copy provides a direct GitHub download while the fresh-runner CI setup is being completed. It is the existing Phase 10 workspace build: 510 tests passed and the shaded jar passed configuration/SQLite verification. See ../VALIDATION.md for its original build evidence.

- Jar: AegisAC-0.10.0-SNAPSHOT.jar
- Size: 12,880,395 bytes
- Requires standalone PacketEvents 2.14.0.
- Compiled against Paper API 1.21.11 / Java 21.
- Folia loading remains disabled; this is not a production-certified release.

The application source was uploaded in commit b96705ed14d0a3ba53163f0a49ee4dc9f004698e. These bytes were built in the earlier validated workspace, not by GitHub Actions. Future Actions builds produce their own checksums and source identifiers.
