import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val addKeystore = file(
    System.getenv("KEYSTORE") ?: "${System.getProperty("user.home")}/tools/compress-keystore.jks"
)

android {
    // Source and R namespace; the applicationId below stays the install id.
    namespace = "add"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.offline.compress"
        minSdk = 26
        targetSdk = 35
        // One above the shipping 3.7 (39): an update installs over it. 40 opened the
        // engine on the editor door; 41 keeps the engine on its own tab and records
        // startup stages, because the crash left no Java trace to read. 42 fixes the
        // saved-document icon crash (empty viewBox) that 41 hit on first draw. 43
        // adds the compositing authoring pass from bd3f7de: editable gradients,
        // all 19 blend modes, mask/clip controls and independent effect switches.
        versionCode = 43
        versionName = "3.7"

        ndk {
            // Review build for a phone. Keeping the x86_64 copy would double the
            // Filament libraries and the handoff download for an emulator this VM
            // cannot run anyway.
            abiFilters += listOf("arm64-v8a")
            // REVIEW_X86=1 adds x86_64 for the headless emulator only. The artifact the
            // handoff serves is built without it and stays arm64, and so does its size.
            if (System.getenv("REVIEW_X86") != null) {
                abiFilters += listOf("x86_64")
            }
        }
    }

    signingConfigs {
        create("addReview") {
            storeFile = addKeystore
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEYSTORE_ALIAS") ?: "compress"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        getByName("debug") {
            // The development flag is the debug build itself. The release build
            // never exposes the engine entry, and build.sh does not carry the code.
            isDebuggable = true
            if (addKeystore.exists()) {
                signingConfig = signingConfigs.getByName("addReview")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    sourceSets.getByName("main") {
        java.srcDirs("src/main/java", "../../../app/src")
        res.srcDirs("src/main/res", "../../../app/res")
        assets.srcDirs("../../../app/assets", "../../engine-spike/app/src/main/assets")
        manifest.srcFile("src/main/AndroidManifest.xml")
    }

    kotlin.sourceSets.getByName("main").kotlin.srcDir(
        "../../engine-spike/app/src/main/java"
    )

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += listOf("glb", "filamat", "ktx")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.github.sceneview:sceneview:2.3.0")
}
