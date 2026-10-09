plugins {
    base
    id("com.gradleup.shadow") version "9.6.1" apply false
}
allprojects {
    group = "dev.aegisac"
    version = "0.11.0-SNAPSHOT"
}
subprojects {
    apply(plugin = "java-library")
    dependencyLocking { lockAllConfigurations() }
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxParallelForks = 2
        testLogging { events("failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.8.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c"
}
