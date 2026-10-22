description = "Leaf Server Connect SDK: builders, sessions, batching and click handling"

val oldestGson = "com.google.code.gson:gson:2.2.4"
val clientGson = "com.google.code.gson:gson:2.10.1"

// The tests check every message against Leaf Client's own Server Connect parser, which is not public.
// Without -PleafClientSources=<path to the client's src/main/java> they are skipped.
val clientSources = providers.gradleProperty("leafClientSources").orNull?.let { rootProject.file(it) }

val clientCore: SourceSet = sourceSets.create("clientCore") {
    if (clientSources != null) java.srcDir(clientSources)
    java.include("com/leafclient/connect/core/**")
}

dependencies {
    compileOnly(oldestGson)

    "clientCoreImplementation"(clientGson)

    testImplementation(clientCore.output)
    testImplementation(clientGson)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<JavaCompile>("compileClientCoreJava") {
    options.compilerArgs.clear()
    options.compilerArgs.add("-nowarn")
    doFirst {
        val parser = File(clientSources, "com/leafclient/connect/core/ConnectState.java")
        check(parser.isFile) {
            "Leaf Client sources not found at $clientSources."
        }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.remove("-Werror")
}

val oldestGsonRuntime: Configuration by configurations.creating

dependencies {
    oldestGsonRuntime(oldestGson)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val testOnOldestGson by tasks.registering(Test::class) {
    description = "Runs the tests with Gson 2.2.4, the version Bukkit 1.8.8 ships."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath.filter { !it.name.startsWith("gson-") } + oldestGsonRuntime
}

val standalone: SourceSet = sourceSets.create("standaloneTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

dependencies {
    "standaloneTestImplementation"(clientGson)
    "standaloneTestImplementation"(platform("org.junit:junit-bom:5.10.2"))
    "standaloneTestImplementation"("org.junit.jupiter:junit-jupiter")
    "standaloneTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.named<JavaCompile>("compileStandaloneTestJava") {
    options.compilerArgs.remove("-Werror")
}

val standaloneTest by tasks.registering(Test::class) {
    description = "Runs the tests that do not need the Leaf Client sources."
    group = "verification"
    testClassesDirs = standalone.output.classesDirs
    classpath = standalone.runtimeClasspath
}

val standaloneTestOnOldestGson by tasks.registering(Test::class) {
    description = "Runs the standalone tests with Gson 2.2.4, the version Bukkit 1.8.8 ships."
    group = "verification"
    testClassesDirs = standalone.output.classesDirs
    classpath = standalone.runtimeClasspath.filter { !it.name.startsWith("gson-") } + oldestGsonRuntime
}

tasks.check {
    dependsOn(testOnOldestGson, standaloneTest, standaloneTestOnOldestGson)
}

if (clientSources == null) {
    listOf("compileClientCoreJava", "compileTestJava", "test", "testOnOldestGson").forEach {
        tasks.named(it) { enabled = false }
    }
}
