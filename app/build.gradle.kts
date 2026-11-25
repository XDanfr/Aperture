import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

android {
    namespace = "me.xdan.aperture"
    compileSdk = 37
    
    defaultConfig {
        applicationId = "cc.xdan.aperture"
        minSdk = 27
        targetSdk = 37
        versionCode = 14
        versionName = "v0.5.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("Boolean", "SIDELOAD_UPDATES_ENABLED", "true")
        buildConfigField("Boolean", "GITHUB_SPONSORSHIP_ENABLED", "true")

        val tmdbApiKey = localProps.getProperty("TMDB_API_KEY") ?: ""
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
        val openSubtitlesApiKey = localProps.getProperty("OPENSUBTITLES_API_KEY") ?: ""
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"$openSubtitlesApiKey\"")
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

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("Boolean", "SPONSOR_PROMPTS_ENABLED", "false")
            buildConfigField("Boolean", "GITHUB_SPONSORSHIP_ENABLED", "false")
            buildConfigField("Boolean", "SIDELOAD_UPDATES_ENABLED", "false")
            if (!System.getenv("PLAY_UPLOAD_STORE_FILE").isNullOrBlank()) {
                // A signed upload without these keys ships without metadata or online subtitles.
                for (key in listOf("TMDB_API_KEY", "OPENSUBTITLES_API_KEY")) {
                    check(!localProps.getProperty(key).isNullOrBlank()) {
                        "$key is required for a signed Play upload"
                    }
                }
                signingConfig = signingConfigs.getByName("playUpload")
            }
        }
        create("standard") {
            dimension = "distribution"
            buildConfigField("Boolean", "SPONSOR_PROMPTS_ENABLED", "true")
        }
        create("creator") {
            dimension = "distribution"
            buildConfigField("Boolean", "SPONSOR_PROMPTS_ENABLED", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    // The app itself lives in :shared (Compose Multiplatform). This module is the Android TV entry
    // point: Application, Activity, manifest, launcher art and build flavours.
    implementation(project(":shared"))
    implementation(project(":core"))

    // The BOM keeps every androidx.compose artifact at the version the TV app has always used,
    // whatever Compose Multiplatform would otherwise resolve.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.coil3.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.room.kmp.runtime)
    androidTestImplementation(libs.coil3.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
