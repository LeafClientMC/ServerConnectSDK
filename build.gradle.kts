subprojects {
    apply(plugin = "java-library")

    group = "com.leafclient.connect"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(8)
        options.compilerArgs.addAll(listOf("-Xlint:all,-options,-processing,-serial", "-Werror"))
    }

    tasks.withType<Javadoc>().configureEach {
        val o = options as StandardJavadocDocletOptions
        o.encoding = "UTF-8"
        o.addBooleanOption("Xdoclint:all,-missing", true)
        o.addBooleanOption("quiet", true)
    }
}

configure(listOf(project(":core"), project(":bukkit"), project(":velocity"), project(":bungee"))) {
    apply(plugin = "maven-publish")
    extensions.configure<PublishingExtension> {
        publications.create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

project(":bukkit") {
    description = "Leaf Server Connect SDK for Bukkit, Spigot, Paper and Purpur 1.8.8 to 26.x"
    dependencies {
        "api"(project(":core"))
        "compileOnly"("org.bukkit:bukkit:1.8.8-R0.1-SNAPSHOT")
        "compileOnly"("com.google.code.gson:gson:2.2.4")
    }
}

project(":velocity") {
    description = "Leaf Server Connect SDK for Velocity"
    dependencies {
        "api"(project(":core"))
        "compileOnly"("com.velocitypowered:velocity-api:3.4.0")
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }
}

project(":bungee") {
    description = "Leaf Server Connect SDK for BungeeCord and Waterfall"
    dependencies {
        "api"(project(":core"))
        "compileOnly"("net.md-5:bungeecord-api:1.21-R0.4")
    }
}

project(":example") {
    description = "Leaf Server Connect example plugin for Bukkit"
    dependencies {
        "implementation"(project(":bukkit"))
        "compileOnly"("org.bukkit:bukkit:1.8.8-R0.1-SNAPSHOT")
        "compileOnly"("com.google.code.gson:gson:2.2.4")
    }
    tasks.named<Jar>("jar") {
        archiveBaseName.set("LeafConnectExample")
        val runtime = configurations.getByName("runtimeClasspath")
        dependsOn(runtime)
        from({ runtime.filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
