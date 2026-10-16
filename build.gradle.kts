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
    // Cross-repository delivery to rhettl/maven is the release worker's assignment.
    repositories { mavenLocal() }
}
