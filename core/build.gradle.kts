description = "Leaf Server Connect SDK: builders, sessions, batching and click handling"

val oldestGson = "com.google.code.gson:gson:2.2.4"
val clientGson = "com.google.code.gson:gson:2.10.1"

val clientSources = rootProject.file(providers.gradleProperty("leafClientSources").get())

val clientCore: SourceSet = sourceSets.create("clientCore") {
    java.srcDir(clientSources)
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
            "Leaf Client sources not found at $clientSources. Set -PleafClientSources=<LeafClientMod/src/main/java>."
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

tasks.check {
    dependsOn(testOnOldestGson)
}
