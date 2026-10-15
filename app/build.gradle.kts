plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release identity comes from the git tag in CI: tag v1.2.3 sets DAILY_VERSION=1.2.3,
// which becomes versionName "1.2.3" and versionCode 10203. Local builds keep 1.0 / 1.
// Android refuses to install a lower versionCode over a higher one, so the code must
// only ever rise; deriving it from the tag makes that a property of the tag rather
// than of anyone remembering to bump it.
val releaseVersion: String? = System.getenv("DAILY_VERSION")?.takeIf { it.isNotBlank() }
val releaseVersionCode: Int? = releaseVersion?.let { v ->
    val parts = v.split(".").map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it >= 0 } && parts[1]!! < 100 && parts[2]!! < 100) {
        "DAILY_VERSION must be MAJOR.MINOR.PATCH with MINOR and PATCH under 100, got '$v'"
    }
    parts[0]!! * 10_000 + parts[1]!! * 100 + parts[2]!!
}

android {
    namespace = "com.christianh.daily"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.christianh.daily"
        minSdk = 31
        targetSdk = 37
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersion ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // CI signs with a keystore decoded from GitHub Actions secrets
        // (.github/workflows/release.yml). The variables are absent in every local
        // build, so this config only exists on the runner.
        val keystore = System.getenv("DAILY_KEYSTORE_FILE")
        if (!keystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("DAILY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DAILY_KEY_ALIAS")
                keyPassword = System.getenv("DAILY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Signed with the author's Android Studio debug keystore (~/.android/debug.keystore):
            // locally through the debug config, in CI through "release" above, which is the
            // same keystore stored as a secret. Same key both ways is the point: the GitHub
            // Release APK installs as an update over the copy already on the author's phone.
            //
            // Why not a dedicated release key: Android will not upgrade an app across a
            // signature change, so switching keys the naive way means an uninstall, and an
            // uninstall deletes notes.json (no export exists, and a release build cannot be
            // read back over adb). The way out that keeps the notes is APK Signature Scheme
            // v3 key rotation (apksigner rotate, then sign with --lineage): the new key proves
            // the old one authorised it. minSdk 31 is above rotation's floor of API 28.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // A separate package for development, so the four-day soak test can run on
        // an untouched release install while work continues (DECISIONS D-012: every
        // reinstall resets the battery-optimisation exemption and voids the run).
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    // org.json ships with Android but is stubbed in the JVM test runtime — every
    // call returns null unless a real implementation is on the test classpath.
    // This is what lets CorpusLoader.parse and NoteStore be tested off-device.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
