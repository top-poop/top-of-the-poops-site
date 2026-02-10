package buildsrc.convention

import com.google.cloud.tools.jib.gradle.JibTask

plugins {
    id("com.google.cloud.tools.jib")
}

tasks.withType<JibTask> {
    notCompatibleWithConfigurationCache("because https://github.com/GoogleContainerTools/jib/issues/3132")
}

jib {
    from.image="eclipse-temurin:21-jre-alpine@sha256:326837fba06a8ff5482a17bafbd65319e64a6e997febb7c85ebe7e3f73c12b11"

    container {
        jvmFlags = listOf(
            "-XX:+AlwaysActAsServerClassMachine",
            "-XX:InitialRAMPercentage=15.0",
            "-XX:MinRAMPercentage=60.0",
            "-XX:MaxRAMPercentage=70.0",
            "-XX:+ExitOnOutOfMemoryError",
            "-XshowSettings:vm",
        )
//        environment = mapOf(
//            "USERNAME" to "user1",
//            "PASSWORD" to "1234"
//        )
//        workingDirectory = "/webservice"
//        volumes = listOf("/data")
//        ports = listOf("8080")
//        args = listOf("--help")
    }
}
