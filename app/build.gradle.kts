plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFilePath = providers.gradleProperty("AI_TRANSLATOR_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("AI_TRANSLATOR_RELEASE_STORE_FILE"))
val releaseStorePassword = providers.gradleProperty("AI_TRANSLATOR_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("AI_TRANSLATOR_RELEASE_STORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("AI_TRANSLATOR_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("AI_TRANSLATOR_RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("AI_TRANSLATOR_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("AI_TRANSLATOR_RELEASE_KEY_PASSWORD"))
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isPresent }

android {
    namespace = "com.clw.aivideotranslator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clw.aivideotranslator"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.4-p0f-hardburn"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyReleaseSigningReady = tasks.register("verifyReleaseSigningReady") {
    group = "verification"
    description = "Fails unless all release-signing inputs are configured and the keystore exists."
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is not configured. Set AI_TRANSLATOR_RELEASE_STORE_FILE, " +
                "AI_TRANSLATOR_RELEASE_STORE_PASSWORD, AI_TRANSLATOR_RELEASE_KEY_ALIAS, and " +
                "AI_TRANSLATOR_RELEASE_KEY_PASSWORD as Gradle properties or environment variables."
        }
        val keystore = rootProject.file(releaseStoreFilePath.get())
        check(keystore.isFile) { "Configured release keystore does not exist: ${keystore.absolutePath}" }
    }
}

// Never allow an unsigned release artifact to be mistaken for a user-ready APK.
// Debug builds remain unaffected; every release pre-build must prove signing readiness first.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseSigningReady)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")

    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
