import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.util.Properties

// Desktop entry point (Windows, macOS, Linux). The app is :shared; this module is the window,
// startup and packaging, as :app is for Android TV.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = "0.5.0"

kotlin {
    jvmToolchain(21)
}

// Same keys the Android build reads, from local.properties, or from the environment for builds
// without one (the AUR source package, Flatpak).
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}
fun apiKey(name: String): String = localProps.getProperty(name) ?: System.getenv(name).orEmpty()

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildConfig")
    val tmdb = apiKey("TMDB_API_KEY")
    val openSubtitles = apiKey("OPENSUBTITLES_API_KEY")
    val version = appVersionName
    inputs.property("tmdb", tmdb)
    inputs.property("openSubtitles", openSubtitles)
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("me/xdan/aperture/desktop/DesktopBuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package me.xdan.aperture.desktop
            |
            |internal object DesktopBuildConfig {
            |    const val VERSION_NAME = "v$version-desktop"
            |    const val TMDB_API_KEY = "$tmdb"
            |    const val OPENSUBTITLES_API_KEY = "$openSubtitles"
            |}
            |""".trimMargin()
        )
    }
}

sourceSets.main {
    kotlin.srcDir(generateBuildConfig)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil3.compose)
}

compose.desktop {
    application {
        mainClass = "me.xdan.aperture.desktop.MainKt"
        // libmpv is loaded through JNA. Windows and macOS builds carry it in mpv-bundle/<os>-<arch>
        // (filled by tools/desktop/*, see docs/DESKTOP.md); Linux packages depend on the system's.
        jvmArgs += listOf("-Dsun.java2d.uiScale.enabled=true")

        nativeDistributions {
            // macOS ships as a DMG made by tools/desktop/package_macos.sh, after ad-hoc signing.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Aperture"
            packageVersion = appVersionName
            description = "A local media player for your own video library"
            vendor = "XDanfr"
            copyright = "© 2026 XDanfr. GPL-3.0."
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("mpv-bundle"))
            modules("java.sql", "java.naming", "jdk.unsupported")

            macOS {
                bundleID = "cc.xdan.aperture.desktop"
                // Apple bundles need a non-zero major version; the in-app version stays 0.5.0.
                packageVersion = "1.0.0"
                dmgPackageVersion = "1.0.0"
                // The bundled Homebrew libmpv only runs on the macOS it was built for or later; the
                // bundler records that version (tools/desktop/bundle_libmpv_macos.py).
                val arch = if (System.getProperty("os.arch") == "aarch64") "arm64" else "x64"
                project.file("mpv-bundle/min-macos-$arch.txt").takeIf { it.exists() }
                    ?.let { minimumSystemVersion = it.readText().trim() }
                // The icon is made with Icon Composer (icons/Aperture.icon). macOS 26 draws the
                // layered Liquid Glass version from Assets.car (CFBundleIconName); earlier
                // versions use the flat renders in the .icns. tools/desktop/compile_mac_icon.sh
                // makes both.
                iconFile.set(project.file("icons/Aperture.icns"))
                infoPlist {
                    extraKeysRawXml = "  <key>CFBundleIconName</key>\n  <string>Aperture</string>\n"
                }
            }
            windows {
                menuGroup = "Aperture"
                menu = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "8b5d3f2e-6c1a-4e57-9a0f-2d7c4b1e9a63"
                iconFile.set(project.file("icons/Aperture.ico"))
            }
            linux {
                packageName = "aperture"
                iconFile.set(project.file("icons/Aperture.png"))
                shortcut = true
                menuGroup = "AudioVideo;Video;Player;"
                appCategory = "video"
                rpmLicenseType = "GPLv3"
            }
        }
    }
}

// Linux packages use the distribution's libmpv rather than bundling one. Debian 12+ and Ubuntu
// 23.04+ call it libmpv2 (older Ubuntu: libmpv1); Fedora's is mpv-libs (H.264/HEVC need RPM
// Fusion's ffmpeg-libs, see docs/DESKTOP.md). The X libraries are for Java's window toolkit,
// which jpackage's own dependency scan misses. Build the .deb on the oldest Ubuntu supported
// (CI uses 22.04): jpackage names the libraries it finds after the build machine's packages.
// Compose writes these into jpackage's argument file unquoted, hence the explicit quotes.
tasks.withType<AbstractJPackageTask>().configureEach {
    when (targetFormat) {
        // Puts Assets.car into Aperture.app/Contents/Resources, beside the .icns. Only the app image
        // takes it; the DMG and PKG are made from that image.
        TargetFormat.AppImage ->
            if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
                freeArgs.addAll("--app-content", "\"${project.file("icons/macos/Resources").absolutePath}\"")
            }
        TargetFormat.Deb -> freeArgs.addAll("--linux-package-deps", "\"libmpv2 | libmpv1, libxtst6, libxi6, libxrender1\"")
        TargetFormat.Rpm -> freeArgs.addAll("--linux-package-deps", "\"mpv-libs, libXtst, libXi, libXrender\"")
        else -> Unit
    }
}

tasks.test {
    // The UI-test rule drives Compose from the test thread, the render tests from the Swing EDT;
    // Compose doesn't allow both in one process, so each test class gets its own JVM.
    forkEvery = 1
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.room.kmp.runtime)
    testImplementation(libs.sqlite.bundled)
}
