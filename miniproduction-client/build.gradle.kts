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
        "com.ahtohiofilho.dominopernambucano.miniproduction.AutonomousPopulationMainKt",
    )
}

dependencies {
    implementation(project(":game-core"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
