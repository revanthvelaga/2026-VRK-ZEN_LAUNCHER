plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zenfold.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zenfold.launcher"
        minSdk = 26
        targetSdk = 34
        // CI builds are numbered by their GitHub Actions run, so App info → Version on the
        // phone shows which build is installed (and each one counts as a newer update).
        val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = ciRun ?: 1
        versionName = if (ciRun != null) "1.0.$ciRun" else "1.0.0-local"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // HorizontalPager/rememberPagerState (ui/HomeScreen.kt) are still
        // @ExperimentalFoundationApi in this Compose Foundation version.
        freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }

    // One fixed debug key, committed on purpose: without it every CI runner signs with a
    // fresh random key, and Android refuses to install a build over one signed with a
    // different key ("App not installed") — so the phone silently keeps the old version.
    // It's the standard public debug-key setup (password "android"), for sideloaded test
    // builds only; a Play release must use its own private key, never this one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("dev.chrisbanes.haze:haze:0.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
