package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm")
    `java-library`
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
//            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}

dependencies {
    testImplementation(lib("junit-jupiter-api"))
    testImplementation(lib("junit-jupiter-engine"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

fun lib(s: String) = the<VersionCatalogsExtension>()
    .find("libs")
    .flatMap { it.findLibrary(s) }
    .get()
