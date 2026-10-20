import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing reads from two places, in order:
//   1. keystore.properties at the repo root - a local, gitignored file for
//      developer machines (see keystore.properties.example).
//   2. Environment variables - used by the GitHub Actions release workflow,
//      which decodes the keystore from a secret at build time.
// Neither the keystore file nor any password is ever committed.
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        FileInputStream(propertiesFile).use { load(it) }
    }
}

fun signingProperty(propertyKey: String, envVar: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(envVar)

val releaseStoreFile = signingProperty("storeFile", "KOTONOTE_STORE_FILE")

android {
    namespace = "com.mugime.kotonote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mugime.kotonote"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Only declared when signing material is actually available, so a
        // checkout without keystore.properties or the env vars can still run
        // assembleDebug / unit tests / lint without a signing error.
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingProperty("storePassword", "KOTONOTE_STORE_PASSWORD")
                keyAlias = signingProperty("keyAlias", "KOTONOTE_KEY_ALIAS")
                keyPassword = signingProperty("keyPassword", "KOTONOTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
