<p align="center">
  <img src="aperture.png" width="96" height="96" alt="Aperture Logo" />
</p>

<h1 align="center">Aperture</h1>

<p align="center">
  <i>Finally, a Material 3 media player for Android TV!</i>
</p>

<p align="center">
  <a href="https://xdan.me/aperture">Website</a> •
  <a href="https://github.com/XDanfr/Aperture/releases/latest">Download the latest alpha</a>
</p>

---

## 📸 Screenshots

<p align="center">
  <img src="screenshots/HomeSide.png" alt="Aperture home screen with the expanded navigation sidebar" />
</p>

<p align="center">
  <img src="screenshots/Popup.png" width="49%" alt="Aperture media details popup" />
  <img src="screenshots/Search.png" width="49%" alt="Aperture search screen" />
</p>

<p align="center"><sub>there are only so many times I can screenshot the angry birds movie</sub></p>

## ✨ Features

* **Material 3 UI** (Eventually, we plan to add dynamic colour support that adapts to your media thumbnails!)
* **TV-Optimised Navigation**
* **Media3 Powered**

## 🗺️ Roadmap

Aperture is currently in **ALPHA**. Here is what is being tracked for future incremental updates:

### Planned Features

- [X] **Popup Animations**: Smooth entrance/exit transitions for the media details modal
- [X] **Progress Indicators**
- [ ] **My List**
- [ ] **Hold-for-actions Context Menu** (https://github.com/XDanfr/Aperture/issues/1)
- [ ] **Movies and TV Shows sorted separately**
- [ ] **TV Show Season and Episode grouping**
- [ ] **Dynamic Theming**: based on active media artwork, or via custom theme selection in settings.
- [ ] **Update Checking**

## 🚀 Getting Started

### Quick Install (Recommended)

Download the latest release `.apk` directly from the [Releases](https://github.com/XDanfr/Aperture/releases/latest) tab and sideload it onto your Android TV device. We recommend using [Downloader by AFTVNews](https://play.google.com/store/apps/details?id=com.esaba.downloader) from the Google Play Store.

### Or: Build From Source

1. **Clone the repository:**

   ```bash
   git clone https://github.com/XDanfr/Aperture.git
   ```

2. **Open in Android Studio:** Ensure your environment is configured to use **JDK 17 or 21** (others are currently untested).
3. **Sync & Deploy:** Sync Gradle, build the project, and deploy it to your physical Android TV device or an emulator running **API 27+**.

## 🛠️ Tech Stack

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Framework:** [Jetpack Compose for TV](https://developer.android.com/training/tv/playback/compose)
* **Media Engine:** [Media3 / ExoPlayer](https://developer.android.com/media/media3)
* **Theming:** Material 3
* **Website Components:** [matraic/m3e](https://github.com/matraic/m3e)

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place! Want a feature or a bugfix? Pull requests are incredibly welcome.

* **Issues:** Open an issue if you discover a new bug or want to propose a fresh feature request.
* **Pull Requests:** Fork the project, create your branch, and submit a PR. Please make sure that your code aligns with the existing project style, unless you're going for a more M3E style as Material 3 transitions towards that look.

## 💬 FAQ

**Q: How are mass local drives parsed?**

**A:** The media architecture leverages standard Android Storage Access Framework processes to securely look up file spaces on attached storage hardware or local nodes.

**Q: Was any AI used for this?**

**A:** Gemini was used as a starter to build the baseline template. Some AI features (like autocomplete) were used to speed up development. However, all code is human-reviewed, manually structured, and the trickier bugs are tackled by hand!

---

<p align="center"><sub>If you like Aperture, please consider <a href="https://github.com/sponsors/XDanfr">supporting its development on GitHub Sponsors</a> 💜</sub></p>

