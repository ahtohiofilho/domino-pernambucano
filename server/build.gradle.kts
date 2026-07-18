plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set(
        "com.ahtohiofilho.dominopernambucano.server.ApplicationKt",
    )
}

dependencies {
    implementation(project(":game-core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.body.limit)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Ktor e Netty usam SLF4J. Sem provider, o runtime descarta os logs.
    runtimeOnly("org.slf4j:slf4j-simple:2.0.18")

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnit()
}
