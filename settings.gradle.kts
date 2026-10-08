pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            content { includeGroupByRegex("io\\.papermc.*"); includeGroup("net.md-5"); includeGroup("com.mojang") }
        }
        maven("https://repo.codemc.io/repository/maven-releases/") {
            content { includeGroup("com.github.retrooper") }
        }
    }
}
rootProject.name = "AegisAC"
include("aegis-api", "aegis-common", "aegis-paper")
