plugins { application }
dependencies {
    implementation(project(":aegis-common"))
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
application { mainClass.set("dev.aegisac.tools.DevelopmentTools") }
tasks.register<JavaExec>("benchmark") {
    group="verification"
    description="Runs JMH; pass -PjmhArgs='...' to select measurements."
    classpath=sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    args(providers.gradleProperty("jmhArgs").getOrElse(".*Benchmark -wi 2 -i 3 -w 1s -r 1s -f 1 -prof gc").split(" ").filter { it.isNotBlank() })
}
