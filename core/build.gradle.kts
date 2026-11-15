plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "me.xdan.aperture.core"
        compileSdk = 37
        minSdk = 27
        androidResources { enable = true }
        optimization {
            consumerKeepRules.publish = true
            consumerKeepRules.file("consumer-rules.pro")
        }
    }
    jvm()
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.material3)
            api(libs.kotlinx.serialization.json)
            implementation(libs.compose.mp.foundation)
        }
        // desktop loads the Outfit font from the same file the Android build packages.
        jvmMain.get().resources.srcDir("src/androidMain/res")
    }
}
