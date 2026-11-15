# Desktop (Compose Multiplatform)

Aperture runs on Windows, macOS and Linux from the same code as the Android TV app. The UI is
not a reimplementation: every screen, dialog, theme and animation is the shared Compose code in
`:shared`, rendered by Compose Multiplatform on the desktop.

## Modules

| Module | What it is |
| --- | --- |
| `:shared` | The app: data, view models, navigation and every screen. Kotlin Multiplatform with `android` and `jvm` targets, built with the Android-KMP library plugin as the [AGP 9 migration guide](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html) describes. |
| `:app` | Android TV entry point: `Application`, `Activity`, manifest, launcher art, build flavours. |
| `:desktop` | Desktop entry point: window, startup and packaging. |
| `:tv-material` | A multiplatform port of `androidx.tv:tv-material` 1.1.0 (see below). |
| `:core` | Theme tokens, brand art and the companion protocol, shared with the phone app. Now multiplatform. |
| `:mobile` | The phone companion, unchanged. |

Because both targets run on the JVM, `commonMain` uses JVM libraries directly (Retrofit, OkHttp,
Moshi, `java.io`). Only Android SDK calls are platform code, behind small interfaces:

| Interface | Android | Desktop |
| --- | --- | --- |
| `PlayerEngine` | Media3/ExoPlayer (+ NextLib FFmpeg) | libmpv |
| `MediaLibrarySource` | MediaStore, storage folders, SAF trees | Folders the user picks |
| `PlaybackPlatform` | `MediaExtractor` checks, SAF subtitles, toasts | mpv decodes everything; in-window toasts |
| `CompanionPlatform` | NSD, `ConnectivityManager` | JmDNS, `NetworkInterface` |
| `KeyValueStores` | The same `SharedPreferences` files | `.properties` files |
| `UpdateManager` | GitHub APK updates (sideload builds) | None |

Dependencies follow the [migration guide](https://kotlinlang.org/docs/multiplatform/migrate-from-android.html):
Hilt became Koin, Coil 2 became Coil 3, Room and DataStore use their multiplatform artifacts, and
Navigation 3, Lifecycle and Material 3 Adaptive use JetBrains' multiplatform builds. On Android
these resolve to the same androidx artifacts as before; `:app` keeps the Compose BOM, so the TV
app's Compose versions don't move.

### TV Material

`androidx.tv:tv-material` is Android-only, but almost pure Compose. `:tv-material` is its 1.1.0
source with the package name kept, so screens import it exactly as before. Only `Platform.kt`
differs between platforms:

- the Surface glow (an Android shadow layer; a Skia outer blur of the same size on desktop),
- key handling (Compose `Key`s, which map 1:1 to the Android key codes),
- Carousel's accessibility check, and
- pointer input: none on Android (unchanged behaviour); on desktop a click focuses and activates a
  surface and moving the mouse moves focus, so the TV focus states show under the pointer. When
  the mouse leaves that item, focus is parked (`PointerFocus`): nothing looks focused until the
  next arrow or Enter key puts it back, and screens hold off their usual focus recovery meanwhile.

## Running

Use JDK 21 (Gradle provisions it if missing).

```sh
./gradlew :desktop:run
```

API keys come from `local.properties`, as for Android (`TMDB_API_KEY`, `OPENSUBTITLES_API_KEY`).

Data lives in the usual per-user locations:

| | Library, settings | Cache (artwork, subtitles) |
| --- | --- | --- |
| macOS | `~/Library/Application Support/Aperture` | `~/Library/Caches/Aperture` |
| Windows | `%APPDATA%\Aperture` | `%LOCALAPPDATA%\Aperture\Cache` |
| Linux | `$XDG_DATA_HOME/aperture` | `$XDG_CACHE_HOME/aperture` |

### Controls

The layouts are the TV's: the window is scaled so it is always 960 dp wide, which is what a 1080p
Android TV reports. Arrow keys and Enter work like a D-pad; Escape, Alt+Left, ⌘[ and the mouse
back button are Back; F11 or Alt/⌘+Enter toggles fullscreen. If nothing has focus, an arrow key
moves focus into the page, so the keyboard always does something.

The mouse works everywhere, without changing the TV app (each behaviour is desktop-only, in
`ui/platform/DesktopPointer.kt` and `:tv-material`'s `PointerFocus`):

- Hovering focuses what's under the pointer, and leaving it clears the highlight (see TV Material
  above). The sidebar opens while the pointer is over it and closes when it leaves.
- Clicking outside a dialog, a menu or the details panel closes it. Their panels are Surfaces,
  which take their own clicks.
- Text fields keep focus while the mouse hovers elsewhere (only a click moves it), and typing on
  the Search page goes to the search box wherever focus is.
- In the player, moving the mouse shows the controls; the cursor hides with them while video
  plays. Clicking the video plays or pauses, double-clicking toggles fullscreen, Space plays or
  pauses, and a click outside the quick menu closes it. On the seek bar, click anywhere to jump
  there or drag to scrub (with the frame preview); hovering previews without seeking. Both player
  layouts have an on-screen Back button, and the details panel a close button. The window frame is dark on
macOS and Windows; on Linux it follows the window manager's theme. The phone companion shows a
laptop or desktop icon for computers (`DeviceKind`, judged by whether there is a built-in battery).

## mpv

Playback uses [libmpv](https://mpv.io) through JNA. mpv renders with its software renderer into
the Compose scene, so the shared player OSD, quick menu and subtitles draw over video just as on
Android. Decoding still uses the GPU where available (`hwdec=auto-copy`); rendering is capped at
1080p and scaled up by Skia. Subtitles come from mpv's `sub-text` and are drawn in Aperture's
subtitle style; scrubbing previews come from a second, silent mpv instance.

### Where libmpv comes from

| | Released app | Running from source (`./gradlew :desktop:run`) |
| --- | --- | --- |
| Windows | Bundled: `libmpv-2.dll` and `vulkan-1.dll` inside the app | Run `tools/desktop/fetch_libmpv_windows.sh` once |
| macOS | Bundled: libmpv and every dylib it needs, inside `Aperture.app` | `brew install mpv` (or bundle it, below) |
| Linux | The distribution's libmpv, a package dependency | `libmpv2` / `mpv-libs` / `mpv` |
| Flatpak | Built into the Flatpak (`/app/lib`) | n/a |

`LibMpv` looks in `APERTURE_LIBMPV_DIR`, then the app's resources folder (where Compose puts
`desktop/mpv-bundle/<os>-<arch>/`), then the usual system locations (Homebrew, `/usr/lib*`,
`/app/lib`). Without libmpv everything but playback works, and the player shows its usual
"Playback failed" notice.

`Aperture --check-libmpv [file]` loads libmpv as playback would, starts and stops a headless mpv,
prints which file it used (`libmpv OK: …/libmpv-2.dll (mpv v0.42.0, client API 2.6)`) and exits
non-zero if that fails. The optional file receives the same line, for the Windows launcher, which
has no console. Every packaging script and CI job runs it on what it built.

## Packaging

Each platform builds on its own OS. Run the scripts from the repository root; results land in
`desktop/build/dist/`. `.github/workflows/build-apps.yml` ("Build apps") runs all of them on every
push and pull request, alongside debug APKs of the TV app and the companion, and keeps the results
as downloadable artifacts of the run. On a `v*` tag it also attaches the desktop builds to a draft
GitHub release. The desktop build needs JDK 21 but no Android SDK.

None of the builds are code-signed by a paid certificate, so first launches warn:

- **Windows:** SmartScreen shows "Windows protected your PC". Choose *More info > Run anyway*.
- **macOS:** the app is ad-hoc signed, not notarised (that needs an Apple Developer account).
  The first time, open it with right-click (or Control-click) > *Open*; on macOS 15 and later,
  try to open it once, then choose *Open Anyway* in *System Settings > Privacy & Security*.
  `xattr -dr com.apple.quarantine /Applications/Aperture.app` also works.

### Windows: MSI and EXE

```sh
tools/desktop/package_windows.sh     # Git Bash
```

`fetch_libmpv_windows.sh` downloads a pinned [shinchiro/mpv-winbuild-cmake](https://github.com/shinchiro/mpv-winbuild-cmake/releases)
`mpv-dev-x86_64` build and checks its SHA-256. Its `libmpv-2.dll` is self-contained (FFmpeg and
everything else linked in) except for `vulkan-1.dll`, the Vulkan loader. GPU drivers usually
install that, but VMs, Windows Server and basic display drivers don't, and without it Windows
refuses to load libmpv at all, so the script also bundles the loader from LunarG's Vulkan Runtime
package (also pinned). Both go in `desktop/mpv-bundle/windows-x64/` with a notices file; JNA loads
libmpv by full path, so Windows finds `vulkan-1.dll` next to it. Then Gradle builds the MSI and
the EXE (Compose fetches WiX itself). The installers offer a folder choice and create Start menu
and desktop shortcuts.

To update libmpv, change `MPV_RELEASE`, `MPV_ASSET` and `MPV_SHA256` at the top of the script
(`gh release view -R shinchiro/mpv-winbuild-cmake --json tagName,assets` lists them with digests).
Use the `x86_64` build, not `x86_64-v3`, which needs AVX2.

### macOS: DMG

```sh
brew install mpv
tools/desktop/package_macos.sh [--sources]
```

1. `bundle_libmpv_macos.py` copies Homebrew's `libmpv.2.dylib` and every library it loads (56
   files, about 86 MB) into `desktop/mpv-bundle/macos-<arch>/`. It rewrites each install name and
   reference to `@loader_path/…`, removes Homebrew rpaths and ad-hoc signs each library. Then it
   checks nothing still points outside the set, and writes `THIRD_PARTY_NOTICES.txt` and
   `licenses/` from Homebrew's metadata.
2. Gradle builds `Aperture.app`; the libraries end up in `Contents/app/resources/`.
3. The script ad-hoc signs a copy of the app (`codesign --sign -`) and verifies it.
4. It runs the app's `--check-libmpv` with `DYLD_PRINT_LIBRARIES` set and fails if any library
   loads from outside the app or macOS. A Mac without Homebrew therefore gets the same result.
5. It makes the DMG (app plus an Applications link) and verifies the app inside it.

Homebrew's libraries run only on the macOS version they were built for, or later. The bundler
records that version, and the build writes it to `LSMinimumSystemVersion`, so an older Mac says
the app needs a newer macOS instead of failing to play. In practice, a DMG built on GitHub's
`macos-15` runners needs macOS 15+; one built on a Mac that compiled Homebrew from source (Homebrew
has no bottles for it) needs that Mac's version. Each DMG is for one architecture; CI builds
`arm64` (Apple Silicon) and `x64` (Intel).

### Linux: deb, rpm, tarball, AUR, Flatpak

```sh
sudo apt install fakeroot rpm        # for jpackage's .deb and .rpm
tools/desktop/package_linux.sh
```

The `.deb` and `.rpm` don't bundle libmpv; they depend on the distribution's:

| Package | Dependency | Notes |
| --- | --- | --- |
| `.deb` | `libmpv2 \| libmpv1` | Debian 12+, Ubuntu 22.04+ (`libmpv1` there) |
| `.rpm` | `mpv-libs` | Fedora. Fedora's FFmpeg leaves out H.264 and HEVC; for those, swap in RPM Fusion's: `sudo dnf swap ffmpeg-free ffmpeg --allowerasing` |

(`desktop/build.gradle.kts` passes these to jpackage as `--linux-package-deps`.) Both install to
`/opt/aperture` with a menu entry. The tarball `Aperture-<version>-linux-x64.tar.gz` is the same
app as a folder; the AUR binary package and the Flatpak use it.

**AUR** (`packaging/aur/`):

- `aperture` builds from the `v<version>` tag's source with `jdk21-openjdk`. API keys come from
  `TMDB_API_KEY` and `OPENSUBTITLES_API_KEY` in the environment, if set.
- `aperture-bin` repackages the release tarball.

Both depend on `mpv` (Arch's package includes libmpv) and install to `/opt/aperture`, with
`/usr/bin/aperture`. Before publishing, set `pkgver`, run `updpkgsums` to replace the `SKIP`
checksums (the release must exist), and generate `.SRCINFO` with `makepkg --printsrcinfo`.

**Flatpak** (`packaging/flatpak/cc.xdan.Aperture.yml`):

```sh
packaging/flatpak/build.sh [--install]
```

builds dav1d, libass, FFmpeg 9.0.2, libplacebo and mpv 0.42.0 from source as modules on the
freedesktop 25.08 runtime, then adds the prebuilt app. The bundle is
`Aperture-<version>-linux-x64.flatpak`. FFmpeg's own decoders handle H.264, HEVC and the rest;
mpv is built with `-Dgpl=false`, so the Flatpak's libmpv is LGPL (see below). The app keeps its
data in `~/.var/app/cc.xdan.Aperture/`. It reads videos from your home folder, `/media`,
`/run/media` and `/mnt`, read-only, because Java's folder picker doesn't use portals. To publish on
Flathub, point the app's source at the release tarball (`url` + `sha256`) and prove ownership of
the `cc.xdan` ID's domain.

## Licences of the bundled libmpv

Aperture is GPL-3.0. What each build distributes alongside it:

| Build | libmpv / FFmpeg configuration | Licence of the bundled code |
| --- | --- | --- |
| Windows (shinchiro) | mpv with its `gpl` feature; FFmpeg with x264 and other GPL parts. No non-free parts (no fdk-aac) | GPL-2.0-or-later as a whole |
| macOS (Homebrew) | mpv with GPL features; FFmpeg `--enable-gpl --enable-version3` | GPL-3.0-or-later as a whole (each library's own licence is in `licenses/`) |
| Linux deb/rpm/AUR | Nothing bundled: the distribution ships libmpv | The distribution's |
| Flatpak | mpv `-Dgpl=false`; FFmpeg without GPL or non-free parts | LGPL-2.1-or-later |

What this means:

- **The combination is allowed.** GPL-2.0-or-later and GPL-3.0-or-later code may be combined with
  GPL-3.0 Aperture, and the combined app is distributed under GPL-3.0, which Aperture already is.
  An LGPL libmpv imposes even less. (The same builds couldn't ship with a closed-source app.)
- **Source must be offered for the bundled binaries, not only Aperture's.** Whoever distributes a
  GPL binary must provide its complete corresponding source (GPL-3.0 section 6). Linking to
  upstream projects isn't enough by itself; the exact versions you shipped must be available from
  you, alongside the download, for as long as you offer the binaries. Concretely:
  - macOS: tag builds run `package_macos.sh --sources`, which collects every bundled formula's
    source archive (`brew fetch --build-from-source`). CI attaches it to the release as
    `Aperture-macos-<arch>-third-party-sources.tar.gz`. Homebrew's patches and build recipes
    live in [homebrew-core](https://github.com/Homebrew/homebrew-core) at those versions.
  - Windows: shinchiro's releases don't include source. The release's `THIRD_PARTY_NOTICES.txt`
    names the mpv commit and build-recipe release, but to be complete, publish the sources
    yourself: build [mpv-winbuild-cmake](https://github.com/shinchiro/mpv-winbuild-cmake) at
    that release (it downloads every component's source) and attach that source tree, or build
    libmpv yourself.
- **Licence texts and notices ship with the app** (`THIRD_PARTY_NOTICES.txt` and `licenses/` in
  the app's resources folder), and the installers show Aperture's GPL-3.0 licence.
- **Codec patents are a separate question from copyright.** H.264, HEVC and similar formats are
  patented in some countries. FFmpeg's licence doesn't grant patent rights, which is why Fedora
  and Flathub's base runtime leave those decoders out. Projects distributing FFmpeg-based players
  for free generally accept this; just be aware of it.

This is a summary, not legal advice.

## Tests

```sh
./gradlew :shared:jvmTest :desktop:test
```

`DesktopRenderTest` boots the real app offscreen (Koin, Room, DataStore in a temp directory) and
writes frames to `desktop/build/render/`, which is a quick way to check desktop rendering without a
display.

`MpvPlaybackTest` makes a short clip with ffmpeg and plays it through the real libmpv: tracks, the
sidecar subtitle, rendered frames, seeking, pausing, the end of the file and scrubbing previews.
Frames go to `shared/build/mpv-test/`. It's skipped where libmpv or ffmpeg isn't installed.

## Not on desktop

- Updates: package managers and installers handle them.
- The legacy-package cleanup, tunnelled playback and Android's codec warnings: they're Android
  concepts, and mpv decodes the formats those warnings are about.
