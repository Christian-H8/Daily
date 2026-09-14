plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Signed with the auto-generated debug keystore (~/.android/debug.keystore).
            // Android refuses to install an unsigned APK, and this app is sideloaded to
            // one phone — a real keystore would mean generating one, storing a password
            // outside git, and guarding it, all to protect an app only its author installs.
            //
            // The cost, worth knowing before the soak install becomes the one kept:
            // Android will not upgrade an app across a signature change, so switching to
            // a real key later requires an uninstall — which takes notes.json with it.
            signingConfig = signingConfigs.getByName("debug")
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
