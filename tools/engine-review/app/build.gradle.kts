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
        // Field backgrounds use the native software compositor, but the preview
        // target is Android 13+ while the engine shell settles.
        minSdk = 33
        targetSdk = 35
        // One above the shipping 3.7 (39): an update installs over it. 40 opened the
        // engine on the editor door; 41 keeps the engine on its own tab and records
        // startup stages, because the crash left no Java trace to read. 42 fixes the
        // saved-document icon crash (empty viewBox) that 41 hit on first draw. 43
        // adds the compositing authoring pass from bd3f7de: editable gradients,
        // all 19 blend modes, mask/clip controls and independent effect switches.
        // 44 routes the review Editor entry straight to the engine; the basic editor
        // remains one tap away in the engine's info sheet. 45 settles every add back
        // into Select with a visible DONE, pauses the 3D surface while the panel is up
        // so it cannot cover its own inspector, and rebuilds the chrome for reading.
        // 46 keeps the settle strip visible until DONE/INSPECT, moves new layers clear
        // of the rail, lifts the strip off the zoom/status row, and files the advanced
        // compositing controls behind a disclosure so the inspector does not end cut.
        // 47 completes the Studio authoring layer: Draggable snapping with live
        // edge/centre guides, a rotation ring, editable MotionPath curves and
        // Observer pan/zoom, all vendored offline with GSAP 3.12.5.
        // 48 fixes 3D layer dragging by keeping pointer input on a stationary
        // node, caps the interactive preview at ~6 MP at high zoom, and caches
        // effect-rendered layers so grain stops redoing full-page work per frame.
        // 49 is the Playful Studio shell: stage-first chrome, Create deck, object
        // bar, Style/FX/Layers inspector, recipe cards, mask and 3D HUDs, and
        // tactile press/spring feedback. Rendering and export paths are unchanged.
        // 50 makes undo/autosave cover the whole document: snapshots carry selection
        // and the entered group, atomic save keeps a last-good backup, and a v4 file
        // with an unknown layer is refused instead of half-decoded.
        // 51 adds real type controls: family resolution with visible fallback,
        // display-only case, tracking and installed font-pack faces.
        // 52 adds a non-destructive source-space photo crop, crop sliders, and a
        // bounded mtime-keyed image decode path shared by preview and export.
        // 53 applies the Pajamas shell on top of the same engine: floating toolbar,
        // segmented inspector tabs, split export, a real layer tree, labelled 48dp
        // targets and reduced-motion chrome.
        // 54 adds the Task 8 deterministic artboard texture library: ten seeded
        // families, a new TEXTURE inspector section, bounded downsampled cache and
        // recipe-only document persistence.
        // 55 adds Material Ink: seeded material strokes authored by gesture, stored
        // as document recipes, rendered through the same Skia artboard as export.
        // It also gives the object bar a readable two-row layout, opens Type controls
        // before Transform, and removes duplicate UNDO rows from History.
        // 56 is the canvas-first BMO shell; 57 completes commands, plugins,
        // responsive panels, legacy-look import and the one-editor release route.
        versionCode = 57
        versionName = "4.0-bmo"
        // The emulator harness cannot reach a non-exported activity through the shell
        // uid. Export it only in the x86_64 review variant; the phone APK stays private.
        manifestPlaceholders["engineExported"] =
            if (System.getenv("REVIEW_X86") != null) "true" else "false"

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
        // The release path reads the shipping ADD key from the documented names; the
        // release build exposes the same ENGINE route as the debug review.
        create("addRelease") {
            storeFile = addKeystore
            storePassword = System.getenv("STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "compress"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        getByName("debug") {
            // The development flag is the debug build itself. Both variants carry the
            // engine now; the release is non-debuggable and signed for install-over.
            isDebuggable = true
            if (addKeystore.exists()) {
                signingConfig = signingConfigs.getByName("addReview")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("addRelease")
        }
    }

    sourceSets.getByName("main") {
        java.srcDirs("src/main/java", "../../../app/src")
        // The engine's own res dir carries the Poppins faces that app/add/engine/R.java
        // bridges into add.R. It also carries Theme.EngineSpike, which the review
        // manifest does not use. Without this the bridge cannot compile.
        res.srcDirs(
            "src/main/res",
            "../../../app/res",
            "../../engine-spike/app/src/main/res",
        )
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

    testImplementation("junit:junit:4.13.2")
}
