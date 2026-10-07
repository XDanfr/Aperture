plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // Android and Compose Desktop both consume this JVM artifact. Keep logic in
    // commonMain so it remains independent of Android, AWT, and java.io APIs.
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
