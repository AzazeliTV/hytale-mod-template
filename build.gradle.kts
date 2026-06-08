import net.ltgt.gradle.errorprone.errorprone

// === Hytale Mod Template — Stand 2026-04-27 ===
// Versionen werden in gradle/libs.versions.toml zentral gepflegt.
// Layout: alle Dependencies nach Destination gruppiert (Sonar kotlin:S6629).

plugins {
    id("java-library")
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.spotless)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.pitest)
    alias(libs.plugins.dependencycheck)
    jacoco
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
    // === compileOnly: vom Hytale-Server / KurashiLib bereitgestellt ===
    compileOnly(libs.hytale.server)
    // compileOnly(files("libs/SomeMod-1.0.0.jar"))
    // compileOnly(fileTree("../kurashi_lib/build/libs/") { include("KurashiLib-*.jar") })
    compileOnly(libs.gson)
    compileOnly(libs.sqlite.jdbc)
    compileOnly(libs.mysql.connector)

    // === errorprone: Compile-time Bug Detection ===
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    // === testImplementation: Test-Frameworks ===
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)

    // === testCompileOnly: Hytale-API fuer Mockito-Mocks ===
    testCompileOnly(libs.hytale.server)
    // testCompileOnly(fileTree("../kurashi_lib/build/libs/") { include("KurashiLib-*.jar") })

    // === testRuntimeOnly: JUnit Platform + Hytale-API fuer Test-JVM ===
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.hytale.server)
    // testRuntimeOnly(fileTree("../kurashi_lib/build/libs/") { include("KurashiLib-*.jar") })
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25

        // Error Prone + NullAway
        options.errorprone {
            disableWarningsInGeneratedCode.set(true)
            allErrorsAsWarnings.set(true)
            option("NullAway:AnnotatedPackages", "de.kurashi")
        }
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        val props = mapOf(
            "group" to project.group,
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
        // Relocate bundled libraries falls implementation() statt compileOnly():
        // relocate("com.google.gson", "de.kurashi.${rootProject.name.lowercase()}.libs.gson")
        from(sourceSets.main.get().output.resourcesDir)
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

// === Tests + Coverage ===
// HINWEIS Java 25 + HytaleLogger: Klassen mit `private static final HytaleLogger LOGGER` brauchen
// die `java.util.logging.manager`-Property mit dem KORREKTEN Klassennamen inkl. `.backend`:
//   com.hypixel.hytale.logger.backend.HytaleLogManager
// Der frueher genutzte `com.hypixel.hytale.logger.HytaleLogManager` (ohne `.backend`) EXISTIERT NICHT
// -> JUL faellt still auf den Default-LogManager zurueck -> HytaleLogger-static-Init wirft
// ExceptionInInitializerError ("Log manager wasn't set"). MIT korrektem Namen laden Logger-Klassen
// sauber und sind direkt instanziierbar/mockbar — kein Pure-Logic-Auslagern noetig fuer Testbarkeit.
// (Das `XxxLogic.java`-Pattern bleibt als Design-Trennung ok, ist aber nicht mehr Pflicht.)
// Verifiziert 2026-06-08: kurashi_lib 314 Tests + HytaleLoggerMockabilityTest gruen.
tasks.test {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)   // Sonar braucht XML
        html.required.set(false)
    }
}

// Error Prone: Nicht auf Tests anwenden
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        enabled = false
    }
}

// === SpotBugs ===
// Deaktiviert: bekannter Bug mit modularem JDK 25 (java/lang/Object.class nicht gefunden).
// Error Prone + NullAway + SonarQube decken Static Analysis bereits ab.
tasks.spotbugsMain { enabled = false }
tasks.spotbugsTest { enabled = false }

spotbugs {
    ignoreFailures.set(true)
    toolVersion.set(libs.versions.spotbugs.tool.get())
}

// === Spotless: Google Java Format (manuell: ./gradlew spotlessApply) ===
spotless {
    java {
        googleJavaFormat().aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
tasks.matching { it.name.contains("spotless") && it.name.contains("Check") }.configureEach { enabled = false }

// === PITest: Mutation Testing (manuell: ./gradlew pitest) ===
pitest {
    targetClasses.set(listOf("de.kurashi.*"))
    threads.set(4)
    outputFormats.set(listOf("HTML"))
    junit5PluginVersion.set("1.2.1")
    mutationThreshold.set(0)
}

// === OWASP Dependency-Check (manuell: ./gradlew dependencyCheckAnalyze) ===
dependencyCheck {
    failBuildOnCVSS = 7.0f
    analyzers.assemblyEnabled = false
}

// === SonarQube ===
sonarqube {
    properties {
        property("sonar.projectKey", rootProject.name)
        property("sonar.host.url", "http://172.17.0.2:9000")
        property("sonar.java.source", "25")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
    }
}

// Sonar braucht test + (falls vorhanden) jacocoTestReport vor sich selbst
tasks.findByName("sonar")?.dependsOn(
    listOfNotNull(tasks.findByName("test"), tasks.findByName("jacocoTestReport"))
)
