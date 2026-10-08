plugins { `java-library` }
dependencies {
    api(project(":aegis-api"))
    implementation("org.yaml:snakeyaml:2.7")
}
