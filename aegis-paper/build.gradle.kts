import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.Properties

plugins { `java-library`; id("com.gradleup.shadow") }
dependencyLocking {
    // Paper's timestamped artifact declares the base SNAPSHOT as its component ID.
    // The exact timestamp below + checksum verification pin it without a conflicting lock.
    ignoredDependencies.add("io.papermc.paper:paper-api")
}
dependencies {
    implementation(project(":aegis-common"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0") { isTransitive = false }
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-20260511.115010-91")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-20260511.115010-91")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    // Match PacketEvents' published provided Netty API for real-buffer decoder fixtures.
    // Test-only: the production server supplies its own Netty; neither jar is bundled.
    testRuntimeOnly("io.netty:netty-buffer:4.1.72.Final")
}
val mockitoAgent = configurations.create("mockitoAgent")
dependencies { mockitoAgent("org.mockito:mockito-core:5.24.0") { isTransitive = false } }
tasks.test {
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    })
}
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.shadowJar {
    archiveBaseName.set("AegisAC")
    archiveClassifier.set("")
    relocate("org.yaml.snakeyaml", "dev.aegisac.internal.yaml")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    from(rootProject.file("LICENSE")) { into("META-INF"); rename { "AegisAC-LICENSE.txt" } }
}
tasks.jar { archiveClassifier.set("unbundled") }
tasks.assemble { dependsOn(tasks.shadowJar) }
val verifyPluginJar = tasks.register("verifyPluginJar") {
    dependsOn(tasks.shadowJar)
    val artifact = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(artifact)
    doLast {
        val file = artifact.get().asFile
        ZipFile(file).use { zip ->
            check(zip.getEntry("plugin.yml") != null) { "Missing Bukkit descriptor" }
            check(zip.getEntry("dev/aegisac/internal/yaml/Yaml.class") != null) { "Parser not relocated" }
            check(zip.entries().asSequence().none {
                it.name.startsWith("org/bukkit/") || it.name.startsWith("com/github/retrooper/") ||
                    it.name.startsWith("io/github/retrooper/") || it.name.startsWith("io/netty/") ||
                    it.name.startsWith("org/yaml/snakeyaml/") || it.name.startsWith("org/openjdk/jmh/") ||
                    it.name.startsWith("dev/aegisac/tools/") || it.name.startsWith("joptsimple/") ||
                    it.name.startsWith("org/apache/commons/math3/")
            }) { "Server dependencies or unrelocated YAML leaked into the distribution" }
        }
        // Load only the distribution and JDK: this catches missing/shading-broken runtime dependencies.
        URLClassLoader(arrayOf(file.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            val directory = Files.createTempDirectory("aegis-jar-smoke-")
            try {
                val type = loader.loadClass("dev.aegisac.common.config.ConfigurationLoader")
                val instance = type.getConstructor(Path::class.java).newInstance(directory)
                val snapshot = type.getMethod("load", Long::class.javaPrimitiveType).invoke(instance, 1L)
                val documents = snapshot.javaClass.getMethod("documents").invoke(snapshot) as Map<*, *>
                check(documents.size == 24) { "Distribution does not generate all configuration documents" }
                val driver = loader.loadClass("org.sqlite.JDBC").getConstructor().newInstance() as java.sql.Driver
                driver.connect("jdbc:sqlite:${directory.resolve("smoke.sqlite")}", Properties()).use { db ->
                    db.createStatement().use { statement ->
                        statement.executeQuery("SELECT sqlite_version()").use { result ->
                            check(result.next() && result.getString(1).isNotBlank()) { "Bundled SQLite driver is unusable" }
                        }
                    }
                }
            } finally { directory.toFile().deleteRecursively() }
        }
    }
}
tasks.check { dependsOn(verifyPluginJar) }
