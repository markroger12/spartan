# Dependency research and pins

Checked against official project publications on 2026-10-05. No dependency version
was selected from a guessed coordinate. All normal fetches use HTTPS; the cloud
build uses the system trust store and platform proxy, with verification enabled.

| Component | Selected version | Official evidence |
| --- | --- | --- |
| Java language/toolchain | 21 | Paper 1.21.11 baseline; installed JDK reports 21.0.12.1 |
| Gradle | 9.8.0 | [Current release metadata](https://services.gradle.org/versions/current), official distribution and wrapper SHA-256 |
| Shadow | 9.6.1 | [Maven Central metadata](https://repo.maven.apache.org/maven2/com/gradleup/shadow/shadow-gradle-plugin/maven-metadata.xml) and Gradle Plugin Portal marker POM |
| Paper API | 1.21.11-R0.1-20260511.115010-91 | [Official snapshot metadata](https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/maven-metadata.xml), [baseline source properties](https://github.com/PaperMC/Paper/blob/ver/1.21.11/gradle.properties) |
| PacketEvents Spigot | 2.14.0 | [Official release](https://github.com/retrooper/packetevents/releases/tag/v2.14.0), [CodeMC metadata](https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-spigot/maven-metadata.xml) |
| SnakeYAML | 2.7 | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/maven-metadata.xml) |
| JUnit Jupiter/BOM | 6.1.3 | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/maven-metadata.xml) |
| Mockito (tests) | 5.24.0 | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/mockito/mockito-core/maven-metadata.xml) |
| MockBukkit v1.21 (tests) | 4.116.3 | [Maven Central metadata](https://repo.maven.apache.org/maven2/org/mockbukkit/mockbukkit/mockbukkit-v1.21/maven-metadata.xml); published module declares Java 21 |

The latest Paper source targets the newer 26.3 beta line, and newer server lines
require newer Java. Phase 2 intentionally selects the maintained 1.21.11 API
baseline, rather than silently claiming current-version or legacy compatibility.
The exact timestamped API artifact avoids a moving SNAPSHOT dependency. Runtime
server versions and client protocol/physics versions will remain separate.

PacketEvents public API declarations were consulted to verify `PacketEventsAPI`,
listener registration/removal, receive/send events, User identity and ClientVersion.
No third-party detector implementations were copied. The plugin depends on the
external PacketEvents instance; it does not call its load/init/terminate methods.

Gradle dependency locks capture resolved configurations. The Paper API is excluded
from the lock because its published component ID remains the base SNAPSHOT and
conflicts with an exact timestamp constraint; its explicit timestamp and checksum
still pin the artifact. SHA-256 verification
metadata is generated from the successful official HTTPS resolutions and checked
on subsequent builds; this provides integrity pinning, not an independent audit of
each upstream artifact. The wrapper jar hash is
`238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5` and distribution hash is
`bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c`.


Phase 2 keeps the verified Phase 1 production dependency pins. Real-buffer tests
add a test-runtime `io.netty:netty-buffer:4.1.72.Final`, matching the Netty API
already declared as provided by PacketEvents 2.14.0 and present in the compile
lock. It is a compatibility fixture dependency, not a new production Netty
selection. The server supplies Netty at runtime; no Netty class is shaded into
AegisAC. Dependency locks were updated and existing checksum verification remains
enabled. PacketEvents public wrapper signatures and version enums were checked
against the installed 2.14.0 artifacts; no upstream implementation was copied.

Phase 3 adds no dependencies and retains all verified production and test pins.
Collision uses the existing Bukkit `Block#getCollisionShape`/`VoxelShape` APIs
on the owning thread, and the common simulator is dependency-free Java.
Offline Gradle resolution of the timestamped Paper artifact may fail even with
the jar present; use the normal checksum-verified build command.

Phase 4 adds no dependency or checksum/lock changes. All evaluators and timing
windows use Java/core data, and owner policy reads use the existing Bukkit API.

Phase 5 also adds no dependency or checksum/lock changes. Target decoding uses the
existing PacketEvents 2.14.0 public wrappers; owner eye/range observation uses the
pinned Paper API. New geometry/statistics/matching code is implemented in common.

Phase 6 adds no dependencies, version changes or lock/checksum changes. It uses the
existing PacketEvents wrappers and Paper block-interaction range attribute. The
user-supplied Paper 26.2/Java 25 log did not establish compatibility: PacketEvents
was missing, so AegisAC never enabled.

Phase 7 adds no dependency or lock/checksum changes. Floodgate/Geyser integration
uses optional reflective linkage to their documented public APIs, loaded only from
enabled provider plugins. AegisAC does not shade, download or initialize either
provider. [BEDROCK.md](BEDROCK.md) links the official contracts inspected and states
the fixture-only validation limit.

## Phase 8 SQLite dependency

`org.xerial:sqlite-jdbc:3.53.4.0` is bundled (including upstream native resources)
with transitives disabled; its optional SLF4J binding is not required. Version was
selected from Maven Central's published metadata. The jar and POM were verified
against their Maven Central HTTPS SHA-256 files; its imported `junit-bom:5.12.2` POM
was similarly verified. Tests still use the locked JUnit 6.1.3 runtime.
No existing verification checksum was changed or removed. The Gradle metadata writer
hit a duplicate Paper snapshot-key error, so the three independently verified entries
were added directly. The shaded-jar check opens SQLite with only a JDK parent loader.

Sources: https://github.com/xerial/sqlite-jdbc and
https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/ .
The JDBC driver version is distinct from Minecraft/server compatibility.

## Fresh GitHub runner metadata (2026-10-09)

A fresh runner exposed 18 missing metadata pins that the original Gradle cache did
not need to fetch. Version selections and dependency locks are unchanged. Existing
verification entries were retained; metadata verification remains enabled.

Fourteen artifacts were fetched over Maven Central HTTPS and checked against its published
SHA-256 sidecars: Groovy BOM 4.0.27 POM/module, Jackson BOM 2.19.2 POM, Jackson parent
2.19.3 POM, JUnit BOM 5.13.1/5.13.4/5.14.1/5.14.2 modules, and FasterXML parent 69 POM. The remaining SHA-256-verified metadata comprises JUnit
BOM 5.12.2/5.10.3/5.7.1 modules, JUnit BOM 6.1.3 POM and Groovy BOM 4.0.22 module.

Four artifacts do not publish SHA-256 sidecars. Their SHA-256 pins were calculated
only after checking the detached PGP signatures from Maven Central:

- Log4j parent and BOM 2.26.1 POMs: verified with ASF Logging's key
  `077E8893A6DCC33DD4A4D5B256E73BA9A0B592D0`, obtained from
  <https://downloads.apache.org/logging/KEYS> over HTTPS.
- Spring Framework BOM 5.3.39 module: verified with key
  `48B086A7D843CFA258E83286928FBF39003C0425`, retrieved by full fingerprint from
  Ubuntu's HTTPS public key server. The same key verified the corresponding Maven
  Central POM signature, and that POM matched the pre-existing SHA-256 pin.

- Guava parent 33.3.1-jre POM: verified with key
  `BDB5FA4FE719D787FB3D3197F6D4A1D411E9D1AE`, retrieved by full fingerprint from
  Ubuntu's HTTPS public key server. The same key verified the Guava 33.3.1-jre
  module, which matched its pre-existing SHA-256 pin.

GPG's local web-of-trust was not initialized or overridden. These are checks of
published signatures/provenance, not a claim of independently certified maintainer
identity. Gradle still enforces SHA-256 for every pinned artifact.
