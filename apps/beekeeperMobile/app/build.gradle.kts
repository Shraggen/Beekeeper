import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bachelorthesis.beekeeperMobile"
    compileSdk = 35

    // This block is a safeguard against duplicate files in library dependencies.
    // By using the @aar dependencies below, this might not be strictly necessary,
    // but it's good practice to keep it.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        applicationId = "com.bachelorthesis.beekeeperMobile"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86"))
        }

        ndkVersion = "25.2.9519653"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("String", "VOSK_MODEL_URL", "\"https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip\"")
            buildConfigField("String", "VOSK_ZIP_FILENAME", "\"vosk-model-small-en-us-0.15.zip\"")

            buildConfigField("String", "LLM_MODEL_URL", "\"https://github.com/Shraggen/Beekeeper/releases/download/v1.0.0/gemma3-270m-it-q8.task\"")
            buildConfigField("String", "LLM_MODEL_FILENAME", "\"gemma3-270m-it-q8.task\"")
        }
        debug {
            buildConfigField("String", "VOSK_MODEL_URL", "\"https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip\"")
            buildConfigField("String", "VOSK_ZIP_FILENAME", "\"vosk-model-small-en-us-0.15.zip\"")

            buildConfigField("String", "LLM_MODEL_URL", "\"https://github.com/Shraggen/Beekeeper/releases/download/v1.0.0/gemma3-270m-it-q8.task\"")
            buildConfigField("String", "LLM_MODEL_FILENAME", "\"gemma3-270m-it-q8.task\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}

dependencies {
    // AndroidX and UI Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    //================================================================//
    // START OF FIX: Use explicit AAR dependencies for Vosk and JNA   //
    // This matches your working project and prevents build errors.   //
    //================================================================//
    implementation(libs.vosk.android)
    implementation(libs.jna)
    //================================================================//
    // END OF FIX                                                     //
    //================================================================//

    // Networking libraries (aligned with versions from your working project)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.cronet.embedded)

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //LLM
    implementation(libs.tasks.genai)
}