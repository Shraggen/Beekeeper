import java.util.UUID

plugins {
    id("com.android.library")
}

android {
    namespace = "org.vosk.models"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
        lint.targetSdk = 35
    }

    buildFeatures {
        buildConfig = false
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("build/generated/assets")
        }
    }
}

tasks.register("genUUID") {
    doLast {
        val uuid = UUID.randomUUID().toString()
        val odir = layout.buildDirectory.get().asFile.resolve("generated/assets/model-en-us")
        val ofile = odir.resolve("uuid")
        odir.mkdirs()
        ofile.writeText(uuid)
    }
}

tasks.named("preBuild") {
    dependsOn("genUUID")
}