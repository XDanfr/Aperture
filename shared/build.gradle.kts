// Aperture's shared app: data, view models, navigation and every screen. The Android TV app
// (:app) and the desktop app (:desktop) are thin entry points around it.
//
// Both targets are JVM based, so commonMain can use JVM libraries (Retrofit, OkHttp, Moshi,
// java.io) directly. Only Android SDK calls live in androidMain, with desktop equivalents in jvmMain.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    android {
        namespace = "me.xdan.aperture.shared"
        compileSdk = 37
        minSdk = 27
        androidResources { enable = true }
        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xannotation-default-target=param-property")
        optIn.addAll(
            "androidx.tv.material3.ExperimentalTvMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":tv-material"))
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.foundation)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.material3)
            implementation(libs.compose.mp.components.resources)
            implementation(libs.compose.mp.ui.tooling.preview)
            implementation(libs.compose.mp.material.icons.core)
            implementation(libs.compose.mp.material.icons.extended)

            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
            api(libs.jetbrains.lifecycle.viewmodel.navigation3)
            api(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.material3.adaptive)
            implementation(libs.jetbrains.material3.adaptive.layout)
            implementation(libs.jetbrains.material3.adaptive.navigation3)
            implementation(libs.androidx.graphics.shapes)

            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)

            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.okhttp)
            implementation(libs.room.kmp.runtime)
            implementation(libs.androidx.datastore.preferences)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.retrofit)
            implementation(libs.converter.moshi)
            implementation(libs.moshi.kotlin)
            api(libs.okhttp)
            implementation(libs.logging.interceptor)
            implementation(libs.zxing.core)
            implementation(libs.jellyfin.sdk.core)
            implementation(libs.kotlinx.serialization.json)
            // org.json ships with Android; desktop gets the same API from the reference library.
            compileOnly(libs.org.json)
        }
        androidMain.dependencies {
            // kotlin-logging (used by the Jellyfin SDK) picks its SLF4J backend and crashes without it.
            implementation(libs.slf4j.api)
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.androidx.palette)
            implementation(libs.accompanist.permissions)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui.compose)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.session)
            implementation(libs.nextlib.media3ext)
            implementation(libs.material)
            implementation(libs.kotlinx.coroutines.android)
        }
        jvmMain.dependencies {
            implementation(libs.org.json)
            implementation(libs.sqlite.bundled)
            implementation(libs.materialkolor.utilities)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.jmdns)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(compose.desktop.common)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.junit)
            implementation(libs.androidx.core)
            implementation(libs.androidx.runner)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
        jvmTest.dependencies {
            // Skia's native library, for tests that render (the mpv playback test).
            implementation(compose.desktop.currentOs)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okhttp.mockwebserver)
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    // Room and Moshi generate code per target from the shared sources.
    add("kspAndroid", libs.room.kmp.compiler)
    add("kspJvm", libs.room.kmp.compiler)
    add("kspAndroid", libs.moshi.kotlin.codegen)
    add("kspJvm", libs.moshi.kotlin.codegen)
}

room {
    schemaDirectory("$projectDir/schemas")
}

compose.resources {
    publicResClass = false
    packageOfResClass = "me.xdan.aperture.resources"
}
