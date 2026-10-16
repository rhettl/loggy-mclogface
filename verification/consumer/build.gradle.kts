plugins { application }
repositories {
    maven {
        url = uri(providers.gradleProperty("loggyRepository").get())
        content { includeModule("io.github.rhettl", "loggy-mclogface") }
    }
    mavenCentral()
}
dependencies {
    runtimeOnly("io.github.rhettl:loggy-mclogface:${providers.gradleProperty("loggyVersion").get()}") {
        // Minecraft/Fabric supply platform dependencies. This fixture models only
        // library class loading and intentionally has no Minecraft installation.
        exclude(group = "net.fabricmc")
        exclude(group = "net.fabricmc.fabric-api")
    }
    runtimeOnly("com.google.code.gson:gson:2.10.1")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
application { mainClass.set("ConsumerSmoke") }
