# Aperture

> Finally, a Material 3 Expressive media player for Android TV!

---

## Screenshots

Coming soon 😝

## Features

* Material 3 UI: Eventually, we plan to add dynamic color support that adapts to your media thumbnails.
* TV-Optimized Navigation: Built specifically for D-pad and remote control inputs.
* Media3 Powered: Robust, high-performance playback architecture.

## Getting Started

### Either:
Download the latest release `.apk` from the [Releases](https://github.com/XDanfr/Aperture/releases/latest) tab if available (recommended)

#### OR:

1. Clone the repo: `git clone [https://github.com/XDanfr/Aperture.git](https://github.com/XDanfr/Aperture.git)`
2. Open in Android Studio: Ensure you are using JDK 17 or 21. I haven't tested any others.
3. Sync Gradle: Build the project and deploy to your Android TV emulator (API 26+) or physical device, or build the APK ready for use.

## Tech Stack

* Language: Kotlin
* UI Framework: Jetpack Compose for TV
* Media: Media3 (ExoPlayer)
* Theming: Material 3 Expressive

## Contributing

Contributions are what make the open-source community so amazing! Want a feature? Make a pull request. Any contributions you make are greatly appreciated.

* Issues: Open an issue if you find a bug or have a feature request.
* Pull Requests: Fork the project and submit a PR. Please ensure your code follows the existing style guidelines.

## FAQ

**Q: How are mass local drives parsed?**
**A:** The media architecture leverages standard Android Storage Access Framework processes to securely look up file spaces on attached storage hardware or local nodes.

**Q: Was any AI used for this?**
**A:** Gemini was used as a starter to build a template. Some AI was used to speed up development and assist me, like autofill. All code was still human-reviewed, structured correctly, and many bugs were fixed manually.
