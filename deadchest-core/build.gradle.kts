plugins {
    id("java")
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Keep compatibility for user with old java version
tasks.named<JavaCompile>("compileJava") {
    options.release.set(8)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.release.set(17)
}


tasks.test {
    useJUnitPlatform()
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.papermc.io/repository/maven-public/") // MockBukkit
}

dependencies {
    // WARNING :
    // 1.20+ InventoryView is now interface
    // cannot update now to prevent regression
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.googlecode.json-simple:json-simple:1.1.1")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.5-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.88.1")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("com.sk89q.worldguard:worldguard-bukkit:7.0.5-SNAPSHOT")

}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

configurations.compileClasspath {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}
