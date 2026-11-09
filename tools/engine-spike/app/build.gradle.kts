plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "app.add.engine"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.add.engine"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // JVM Compose frames: Robolectric's native graphics draw the real activity, and
    // Roborazzi writes the PNG. No emulator, no Filament surface on the captured states.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
        }
    }


    androidResources {
        noCompress += listOf("glb", "filamat", "ktx")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The chrome test compares every frame it renders against the golden on disk, so those
// PNGs are an input even though Gradle cannot infer it. Without this the task stays
// UP-TO-DATE and the guard reports success without comparing anything; declaring the
// directory makes a refreshed, moved or corrupted baseline re-run the comparison.
tasks.withType<Test>().configureEach {
    val goldens = file(System.getenv("ENGINE_RENDER_OUT") ?: "src/test/screenshots")
    inputs.dir(goldens).withPropertyName("engineChromeGoldens").optional()
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

    // SceneView 2.3.0 is pinned to Filament 1.56 and Kotlin 2.0.21.
    implementation("io.github.sceneview:sceneview:2.3.0")

    testImplementation("junit:junit:4.13.2")
    // The real org.json, so the document round trip is tested on the JVM instead of
    // against the stubbed classes in the mockable android.jar.
    testImplementation("org.json:json:20240303")

    // Screenshot path for the engine chrome. Roborazzi records the Compose frame that
    // Robolectric draws through the platform's native graphics, so it needs no device.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.44.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.44.0")
}
