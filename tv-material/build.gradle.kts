// Multiplatform port of androidx.tv:tv-material 1.1.0 (Apache 2.0). The sources are upstream's;
// the only edits route the few Android framework calls through Platform.kt so the
// same components render on desktop. Package names are kept so app code imports are unchanged.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "androidx.tv.material3.port"
        compileSdk = 37
        minSdk = 27
    }
    jvm()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("androidx.tv.material3.ExperimentalTvMaterial3Api")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.foundation)
            api(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            implementation("androidx.annotation:annotation:1.9.1")
        }
    }
}
