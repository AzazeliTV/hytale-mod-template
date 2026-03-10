plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
}

group = "de.kurashi"
version = "1.0.0"
description = "Template Hytale Server Mod"

repositories {
    mavenCentral()
    maven {
        name = "hytale"
        url = uri("https://maven.hytale.com/release")
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:+")

    // Bundled dependencies (included in JAR via shadowJar):
    // implementation("com.google.code.gson:gson:2.10.1")
    // implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    // Compile-only mod dependencies (provided at runtime):
    // compileOnly(files("libs/SomeMod-1.0.0.jar"))
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        val props = mapOf(
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)

        filesMatching("manifest.json") {
            expand(props)
        }
        filesMatching("version.properties") {
            expand(props)
        }
    }

    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")

        // Relocate bundled libraries to avoid classpath conflicts:
        // relocate("com.google.gson", "de.kurashi.template.libs.gson")

        minimize()
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
