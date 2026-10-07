# Multiplatform progress

This branch introduces a Compose Multiplatform desktop application and shared
filename parsing. It is an incremental migration, not Android/desktop feature
parity. The existing Android application, TV theme, remote instructions, Media3
player, Room database and metadata integrations remain in `app`.

| Module | Purpose |
| --- | --- |
| `app` | Android TV and touch devices; Android playback and library |
| `core` | Platform-independent Kotlin logic in `commonMain`; JVM artifact consumed by both apps |
| `desktop` | Compose Desktop JVM target for Windows, macOS and Linux |

## Run and package desktop

Use JDK 21, as configured by this repository's Gradle daemon toolchain.

```sh
./gradlew :desktop:run
```

Windows uses `gradlew.bat :desktop:run`.

```sh
./gradlew :desktop:packageDistributionForCurrentOS
```

The packaging configuration supports DMG on macOS, MSI on Windows, and DEB on
Linux. Build installers on their matching OS; see the
[Compose packaging documentation](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html).
The macOS installer uses package version `1.0.0` because Apple's format rejects
zero major versions; the project remains a pre-alpha 0.5 development branch.
Installers are not signed/notarized by this configuration.

Desktop supports folder selection through a platform-styled directory chooser,
background recursive scanning, search, movie/episode filters, persistent folders
and My List, sidebar hover, Escape/back and click-outside media dialogs. Library
folders can be removed without deleting any media. Overlapping roots are
deduplicated, directory symlinks are not followed, and inaccessible folders are
reported in Folders. Cancelling the chooser leaves the library unchanged.

Desktop playback opens the selected file in the OS-associated video application.
Install/associate a player if the OS has none. It does not yet embed Media3/VLC,
track playback progress, fetch TMDB art/metadata, download subtitles, or share the
Android library database. Shows currently lists parsed episodes, not grouped
series. These limitations are also explained in the desktop Folders screen.

Desktop settings are stored in `Aperture/library.properties` beneath:

- Windows: `%APPDATA%`
- macOS: `~/Library/Application Support`
- Linux: `$XDG_CONFIG_HOME`, falling back to `~/.config`

No API keys are needed by the desktop application.

## Android input changes

- TV buttons, surfaces, icons and sidebar items have a shared pointer adapter
  that retains native remote handling and pressed-state feedback.
- Leanback is optional, so touch-only Android devices are eligible to install.
- Escape routes to the existing back dispatcher; dialogs handle Escape in their
  own windows. Key-up activates back once, preserving held remote key events.
- Sidebar hover expands the rail and contracts a rail opened by hover. Keyboard
  focus can still keep a deliberately opened drawer visible.
- Details and context menus close on outside taps, with explicit Close buttons.
  The details panel scrolls when its contents do not fit.
- Media cards support touch long-press independently of the existing remote
  hold timer and opening-key-release suppression.
- Tapping the video reveals player controls. The thin layout's menu chevron is
  clickable, the classic layout has a back button, and timelines accept pointer
  seeking. Quick-menu outside taps resume playback only if opening it paused
  previously playing media.
- Quick-menu pages expose Back/Close buttons; Escape retains nested back handling.

The Android UI still uses many TV-sized layouts. Small portrait phone screens
need a further adaptive layout pass; landscape/tablet layouts are the initial
touch testing target.

## Verification

```sh
./gradlew :core:jvmTest :desktop:jvmTest :app:testStandardDebugUnitTest :app:assembleStandardDebug
```

Validated on macOS: all 14 JVM/unit tests pass, the standard debug APK builds,
the three Android input instrumentation tests compile, and the packaged macOS
application starts. Device tests have not been executed because no authorized
Android device/emulator was available. Windows/Linux installers and interactions
still require testing on those hosts.

To run the input tests with an authorized device/emulator attached:

```sh
./gradlew :app:connectedStandardDebugAndroidTest
```

The shared tests include Windows path parsing on Unix. Desktop tests cover
settings round-trips with Unicode paths, overlapping scans, ignored non-video
files, and missing-folder reporting.

Manual checks on each target OS/device:

1. Add a directory, cancel another selection, add an overlapping directory,
   restart, and confirm folders/My List persist. Test an unavailable drive.
2. Open media, click blank space inside the panel (it stays open), then outside
   (it closes). Escape and Close should each dismiss exactly one layer.
3. Hover the sidebar, leave it, then navigate by keyboard/D-pad. Confirm focus
   restoration and remote short/long presses, including holding Enter while a
   context menu opens.
4. On Android touch, open details, play, tap to reveal controls, seek, open the
   quick menu and dismiss it. Check both initially playing and paused states.
5. In both Android player layouts, use Back to return to the library. Check
   Escape inside nested subtitle settings and cancel active remote scrubbing.
6. On desktop, play a video through its associated system player. Test Windows
   and Linux separately; a macOS build does not validate their integration.

## Next migration steps

Move media models/repository contracts into `core`, implement desktop persistence
and metadata adapters, and extract a shared Material Compose UI with TV-specific
focus behavior at the Android boundary. Embedded desktop playback needs its own
engine adapter and a progress/subtitle integration before feature parity can be
claimed. The current Android player interfaces expose Media3 types and cannot
be moved directly to `commonMain`.
