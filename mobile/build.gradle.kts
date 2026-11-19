plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "me.xdan.aperture.mobile"
    compileSdk = 37

    defaultConfig {
        // same Play listing as the TV app: phones get this build, TVs get the TV app from
        // Play's Android TV track. they never share a device (TV build requires leanback,
        // this one a touchscreen).
        applicationId = "cc.xdan.aperture"
        minSdk = 29
        targetSdk = 37
        // version codes must be unique across every bundle in the listing. the companion
        // uses 2,000,000+ so it never collides with the TV app's codes.
        versionCode = 2_000_001
        versionName = "v0.1.0-pre-alpha"
    }

    signingConfigs {
        create("playUpload") {
            val uploadStore = System.getenv("PLAY_UPLOAD_STORE_FILE")
            if (!uploadStore.isNullOrBlank()) {
                storeFile = file(uploadStore)
                storePassword = System.getenv("PLAY_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("PLAY_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("PLAY_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // signed with the Play upload key only when CI supplies it; otherwise unsigned.
            if (!System.getenv("PLAY_UPLOAD_STORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.play.services.code.scanner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
