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
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.bachelorthesis.beekeeperMobile"
    compileSdk = 36

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
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		
		externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17" // Use C++17 standard
            }
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        ndkVersion = "25.2.9519653"
        signingConfig = signingConfigs.getByName("debug")

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

            buildConfigField("String", "LLM_MODEL_URL", "\"https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task\"")
            buildConfigField("String", "LLM_MODEL_FILENAME", "\"gemma3-270m-it-q8.task\"")

            buildConfigField("String", "WHISPER_MODEL_URL", "\"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q8_0.bin\"")
            buildConfigField("String", "WHISPER_MODEL_FILENAME", "\"ggml-base-q8_0.bin\"")

            buildConfigField("String", "VAD_MODEL_URL", "\"https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin\"")
            buildConfigField("String", "VAD_MODEL_FILENAME", "\"ggml-silero-v5.1.2.bin\"")
			
			externalNativeBuild {
                cmake {
                    // This enables high-performance builds
                    cFlags += "-O3"
                    cppFlags += "-O3"
                }
            }
        }
        debug {
            buildConfigField("String", "VOSK_MODEL_URL", "\"https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip\"")
            buildConfigField("String", "VOSK_ZIP_FILENAME", "\"vosk-model-small-en-us-0.15.zip\"")

            buildConfigField("String", "LLM_MODEL_URL", "\"https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.task\"")
            buildConfigField("String", "LLM_MODEL_FILENAME", "\"gemma3-270m-it-q8.task\"")

            buildConfigField("String", "WHISPER_MODEL_URL", "\"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q8_0.bin\"")
            buildConfigField("String", "WHISPER_MODEL_FILENAME", "\"ggml-base-q8_0.bin\"")

            buildConfigField("String", "VAD_MODEL_URL", "\"https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin\"")
            buildConfigField("String", "VAD_MODEL_FILENAME", "\"ggml-silero-v5.1.2.bin\"")

            externalNativeBuild {
                cmake {
                    // This enables high-performance builds
                    cFlags += "-O3"
                    cppFlags += "-O3"
                }
            }
        }
    }
	
	externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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
    //================================================================//
    // END OF FIX                                                     //
    //================================================================//

    // Networking libraries (aligned with versions from your working project)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.cronet.embedded)

    // NEW: Room Persistence Library
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.preference.ktx)
    ksp(libs.room.compiler)

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
    implementation("com.google.mediapipe:tasks-vision:latest.release")
    implementation("com.google.mediapipe:tasks-text:latest.release")
    implementation("com.google.mediapipe:tasks-audio:latest.release")

    implementation(libs.androidx.preference.ktx)
}