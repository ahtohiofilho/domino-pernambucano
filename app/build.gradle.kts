plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun String.toBuildConfigString(): String {
    val escapedValue = replace("\\", "\\\\")
        .replace("\"", "\\\"")

    return "\"$escapedValue\""
}

val releaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("release", ignoreCase = true)
}
val releaseStoreFile = providers
    .environmentVariable("DOMINO_UPLOAD_STORE_FILE")
    .orNull
val releaseStorePassword = providers
    .environmentVariable("DOMINO_UPLOAD_STORE_PASSWORD")
    .orNull
val releaseKeyAlias = providers
    .environmentVariable("DOMINO_UPLOAD_KEY_ALIAS")
    .orNull
val releaseKeyPassword = providers
    .environmentVariable("DOMINO_UPLOAD_KEY_PASSWORD")
    .orNull
val googleWebClientId = providers
    .gradleProperty("googleWebClientId")
    .orElse(
        providers.environmentVariable("DOMINO_GOOGLE_WEB_CLIENT_ID"),
    )
    .orElse("")
    .get()
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

if (releaseBuildRequested) {
    check(releaseSigningConfigured) {
        "Release signing requires the DOMINO_UPLOAD_* process environment."
    }
    check(googleWebClientId.isNotBlank()) {
        "Release Google sign-in requires DOMINO_GOOGLE_WEB_CLIENT_ID " +
            "or -PgoogleWebClientId."
    }
}

android {
    namespace = "com.ahtohiofilho.dominopernambucano"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ahtohiofilho.dominopernambucano"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            type = "String",
            name = "GOOGLE_WEB_CLIENT_ID",
            value = googleWebClientId.toBuildConfigString(),
        )
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFile))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            val onlineBackendMode = providers
                .gradleProperty("onlineBackendMode")
                .orElse("fake")
                .get()

            val onlineBackendBaseUrl = providers
                .gradleProperty("onlineBackendBaseUrl")
                .orElse("")
                .get()

            buildConfigField(
                type = "String",
                name = "ONLINE_BACKEND_MODE",
                value = onlineBackendMode.toBuildConfigString(),
            )

            buildConfigField(
                type = "String",
                name = "ONLINE_BACKEND_BASE_URL",
                value = onlineBackendBaseUrl.toBuildConfigString(),
            )
        }

        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }

            buildConfigField(
                type = "String",
                name = "ONLINE_BACKEND_MODE",
                value = "remote".toBuildConfigString(),
            )

            buildConfigField(
                type = "String",
                name = "ONLINE_BACKEND_BASE_URL",
                value = "https://api.dominope.com.br".toBuildConfigString(),
            )

            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":game-core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
