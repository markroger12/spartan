# Licensing and third-party notices

AegisAC is original code distributed under GPL-3.0-only (see LICENSE), chosen for
compatibility with its GPL PacketEvents integration. No proprietary anti-cheat
source is included. No third-party detection algorithm was copied or translated.

The runtime jar bundles AegisAC API/common code and relocates SnakeYAML (Apache-2.0).
SnakeYAML's own META-INF notices/license are retained by the jar build. PacketEvents
(GPL-3.0) and Bukkit/Paper APIs are provided separately by the server and are not
bundled. Distributing an AegisAC binary requires supplying corresponding AegisAC
source under its license; do not market this as a closed-source derivative.

Gradle and Shadow are build tools. JUnit (EPL-2.0), Mockito (MIT) and MockBukkit
(MIT) are test dependencies and are not bundled in the plugin jar. Consult each
upstream publication for its full license and transitive notices. This repository
does not incorporate other anti-cheat products as runtime dependencies.

Phase 2 decoder tests also use the provided Netty API (Apache-2.0) at test runtime.
It is not included in the distributed plugin.

Phase 8 bundles Xerial SQLite JDBC 3.53.4.0 (Apache-2.0) and its upstream native
resources. Its packaged `META-INF/maven/org.xerial/sqlite-jdbc/LICENSE` and
`LICENSE.zentus` (original Zentus BSD notice) are retained. SQLite itself is public
domain; see the upstream SQLite/Xerial distribution for native-code notices.
Java's standard HTTP client supplies webhook transport; no HTTP client library is added.
