plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-jib")
    application
}

group ="org.totp"

dependencies {
    implementation(libs.bundles.http4k)
    implementation(libs.bundles.http4k.server)
    implementation(libs.bundles.http4k.client)
    implementation(libs.bundles.http4k.frontend)
    implementation(libs.bundles.forkhandles)
    implementation(libs.bundles.caffeine)
    implementation(libs.bundles.database)

    implementation(libs.redis)
    implementation(libs.slf4j.nop)
    implementation(libs.opencsv)
    implementation(libs.pretty.time)

    implementation(libs.bundles.kotlinxHtml)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.jsoup)

}

application {
    mainClass = "org.totp.MainKt"
}

jib {
    to.image = "totp-pages"
}
