<p align="center">
  <img src="aperture.png" width="96" height="96" alt="Aperture Logo" />
</p>

<h1 align="center">Aperture</h1>

<p align="center">
  <i>Finally, a Material 3 media player for Android TV!</i>
</p>

---

## 📸 Screenshots

Coming soon 😝

## ✨ Features

* **Material 3 UI** (Eventually, we plan to add dynamic color support that adapts to your media thumbnails!)
* **TV-Optimized Navigation**
* **Media3 Powered**

## 🗺️ Roadmap

Aperture is currently in **ALPHA**. Here is what is being tracked for future incremental updates:

### Active Bug Squashing
- [x] **Scrolling Stability**: Fixed view snapping back to the top when scrolling down
- [x] **Popup Bounds**: Fixed scrolling outside the media details popup
- [ ] **Sidebar Interference**: Fix left-navigation from the popup accidentally invoking the sidebar
- [ ] **Player UI Invocation**: Fix the OSD fading out immediately before it can be reliably used
- [ ] **Settings UI**: Fix buttons enlarging/cutting off and displaying behind the menu layers
- [ ] **Onboarding Flash**: Eliminate the split-second flash of the onboarding screen on authorized launches

### Planned Features
- [ ] **Popup Animations**: Smooth entrance/exit transitions for the media details modal
- [ ] **Progress Indicators**
- [ ] **My List**
- [ ] **Movies and TV Shows sorted separately**
- [ ] **Dynamic Theming**: True Material 3 dynamic coloring based on active media artwork

## 🚀 Getting Started

### Quick Install (Recommended)
Download the latest release `.apk` directly from the [Releases](https://github.com/XDanfr/Aperture/releases/latest) tab and sideload it onto your Android TV device. We recommend using [Downloader by AFTVNews](https://play.google.com/store/apps/details?id=com.esaba.downloader) from the Google Play Store.

### OR

### Build From Source
1. **Clone the repository:**

   ```bash
   git clone [https://github.com/XDanfr/Aperture.git](https://github.com/XDanfr/Aperture.git)
   ```

2. **Open in Android Studio:** Ensure your environment is configured to use **JDK 17 or 21** (others are currently untested).
3. **Sync & Deploy:** Sync Gradle, build the project, and deploy it to your Android TV physical device or an emulator running **API 27+**.

## 🛠️ Tech Stack

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Framework:** [Jetpack Compose for TV](https://www.google.com/search?q=https://developer.android.com/tv/compose)
* **Media Engine:** [Media3 / ExoPlayer](https://developer.android.com/media/media3)
* **Theming:** Material 3

## 🤝 Contributing

Contributions are what make the open-source community such an amazing place! Want a feature or a bugfix? Pull requests are incredibly welcome.

* **Issues:** Open an issue if you discover a new bug or want to propose a fresh feature request.
* **Pull Requests:** Fork the project, create your branch, and submit a PR. Please do make sure that your code aligns with the existing project style, unless you're going for a more M3E style as Material 3 transitions towards that look.

## 💬 FAQ

**Q: How are mass local drives parsed?**

**A:** The media architecture leverages standard Android Storage Access Framework processes to securely look up file spaces on attached storage hardware or local nodes.

**Q: Was any AI used for this?**

**A:** Gemini was used as a starter to build the baseline template. Some AI features (like autocomplete) were used to speed up development. However, all code is human-reviewed, manually structured, and the trickier bugs are tackled by hand!
