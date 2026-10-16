plugins {
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("maven-publish")
}

group = property("maven_group") as String
version = property("mod_version") as String
base.archivesName.set("loggy-mclogface")

architectury {
    platformSetupLoomIde()
    fabric()
}
loom { silentMojangMappingsLicense() }
repositories { mavenCentral() }
dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Nested into Loggy's remapped runtime jar. Minecraft 1.21.1 supplies Gson 2.10.1;
    // mclogs-java's only runtime dependency is Gson 2.12.1, so keep that second Gson out.
    val mclogsApi = implementation("gs.mclo:api:${property("mclogs_version")}") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    include(mclogsApi)
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit_version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
tasks.test { useJUnitPlatform() }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar {
    from("LICENSE") { rename { "LICENSE_loggy-mclogface" } }
    from("THIRD-PARTY-NOTICES.md")
}
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "loggy-mclogface"
            from(components["java"])
            pom {
                name.set("Loggy McLogface")
                description.set("Server troubleshooting reports with registered mod diagnostics")
                url.set("https://github.com/rhettl/loggy-mclogface")
                licenses { license { name.set("MIT"); url.set("https://opensource.org/license/mit") } }
            }
        }
    }
    repositories {
        mavenLocal()
        // A local plain Maven layout for consumers and packaging verification.
        // Cross-repository publishing is intentionally absent until its destination
        // and write credentials are confirmed.
        maven {
            name = "LocalMaven"
            url = uri(layout.buildDirectory.dir("maven-repository"))
        }
    }
}
