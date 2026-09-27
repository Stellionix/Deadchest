import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    compileTestJava {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
}

val pluginDir: String by lazy {
    project.findProperty("pluginDir") as? String ?: "Missing plugins folder path"
}

val targetDir: String by project.extra
tasks.register("copyJar", Copy::class) {
    dependsOn("shadowJar")
    from(tasks.shadowJar)
    into(pluginDir)
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml") {
        expand("pluginVersion" to pluginVersion)
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.papermc.io/repository/maven-public/") // MockBukkit
}

dependencies {
    implementation(project(":deadchest-core"))
    implementation("org.bstats:bstats-bukkit:2.2.1")
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
}

configurations.all {
    resolutionStrategy {
        force("io.papermc.paper:paper-api:1.20.4-R0.1-20240205.114523-90")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    relocate("org.bstats", "me.crylonz.deadchest")
    archiveFileName.set("dead-chest-${project.version}.jar")
}

val spaceUsername: String by project
val spacePassword: String by project

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "dead-chest"
            version = project.version.toString()
            from(components["java"])
        }
    }
}
