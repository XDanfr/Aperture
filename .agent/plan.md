# Project Plan

Aperture: Premium Android TV media player with cinematic glassmorphic UI.
Architecture: MVVM, StateFlow, Room, Hilt, Media3, Retrofit, Coil.
Key Features: TMDB/OpenSubtitles integration, Regex parser, Focus/Click physics, Persistent Drawer with focus memory, Ambient Mode, Search UI, OSD with Quick Menu, Onboarding, Settings.
Design: Glassmorphic, 1.05x focus scaling, 0.95x click bounce, dark gradient scrims.

## Project Brief

# Context & Objective
Act as a Staff-level Android TV UI/UX Engineer and Kotlin architecture expert. I am building "Aperture," a highly polished, feature-complete Android TV media player. It plays local files but its UI must perfectly mimic the premium, cinematic, glassmorphic feel of modern streaming giants like Netflix and Apple TV.

Your task is to thoroughly analyze this brief and generate a highly detailed `plan.md` that outlines the MVVM architecture, Jetpack Compose for TV package structure, and step-by-step implementation guide. Once I approve the plan, you will begin generating the code.

# Tech Stack & Standards
- **Language**: Kotlin.
- **UI Toolkit**: Jetpack Compose for TV (use `androidx.tv:tv-material:1.1.0` or later for stable Material 3 TV components). 
- **Architecture**: MVVM, `StateFlow`, Room Database (for local state), and Hilt for Dependency Injection.
- **Media Engine**: Media3 (ExoPlayer) version `1.10.1` or later, configured for highly optimized local playback and hardware audio passthrough.
- **Metadata**: TMDB API via Retrofit to fetch cinematic posters, backdrops, and exact metadata.
- **Subtitles**: OpenSubtitles REST API for on-the-fly subtitle downloading and extraction.
- **Image Loading**: Coil. *Crucial constraint*: Configure Coil to load crisp, native-resolution images and disable low-res crossfading to prevent any blurriness or temporal ghosting during fast D-pad scrolling.

# Provided Reference Material
I have attached four reference images (`image_d49d9a.jpg`, `image_d49da0.jpg`, `image_d49e33.jpg`, `image_d4a161.jpg`). Use these strictly to understand the desired layout proportions, the heavy bottom-to-top gradient fades on hero images, and the high-contrast focus states.

# Reference Code & Architecture Strategy
When generating the local network/file scanning logic, do not invent heavily bloated background services. Mimic the lightweight, highly efficient approaches seen in open-source TV projects like `laposa/media-player` or `Nova Video Player`.

# Core UX, Micro-Interactions & State Logic

## 1. Micro-Interactions & Animation
- **Focus States**: When an item receives D-pad focus, it should smoothly scale up (e.g., 1.05x) with a subtle spring animation. Apply a high-contrast Material 3 border/glow.
- **Click Physics**: Implementing `Modifier.clickable` or custom `Indication` must include a slight native Android "bounce" (scaling down to 0.95x momentarily) when the select button is pressed, mirroring the system launcher's app-opening effect.
- **Overscroll**: Apply standard Leanback overscroll bounce effects when the user hits the end of a `TvLazyRow`.

## 2. Navigation & Spatial Memory (CRITICAL)
- **Persistent Drawer**: A slim left-side `NavigationDrawer` (Search, Home, Shows, Movies, Settings) that expands when navigating left.
- **Focus Memory**: 
  - If a user is deep in a horizontal content row and presses "BACK", focus must instantly snap to the top Navigation Drawer/Hero area *without* losing their X/Y scroll position in the row below. 
  - If they press "DOWN" or press "BACK" again while focus is on that top menu/drawer, focus must drop seamlessly back to the exact poster they were previously hovering over in the content row.
- **Dynamic Refresh**: Returning to the Home screen triggers a soft refresh. Row categories (e.g., "Continue Watching", "Because You Watched...") stay strictly in the same vertical order, but content within them shuffles slightly to feel alive.

## 3. The Dashboard & Metadata
- **Hero Carousel**: A `FeaturedCarousel` at the top that auto-scrolls, utilizing a dark gradient scrim so overlaying text (Title, Year, TMDB tags, and tech badges like 4K/HDR) remains legible. The banner should showcase the last watched local item mixed with recommendations.
- **Media Details Modal**: Clicking a poster triggers a smooth, immersive slide-out modal overlay or sidebar layout (do not open a new Activity). It displays the TMDB synopsis, Cast & Crew `TvLazyRow`, and action buttons: **Play**, **Add to My List**, and **View Episodes** (if a TV series).
- **Regex Cleaning**: Implement a robust Regex parser to clean raw local file names (e.g., stripping out '1080p', 'x264', download tags, release years) before querying the TMDB API to ensure high match accuracy.

## 4. Search UI
- When navigating to the Search tab, present a clean TV-optimized software keyboard layout or voice-search prompt hook.
- As the user inputs text, a grid (`TvLazyVerticalGrid`) on the right side of the screen must dynamically populate with local file results matching the query, maintaining the 2:3 poster aspect ratio.

## 5. Player UI & Features
- A transparent On-Screen Display (OSD) that auto-hides after 3 seconds of user inactivity.
- **State Persistence**: Automatically save the exact playback timestamp on video exit/pause to a Room database to accurately populate progress bars and the "Continue Watching" row.
- **Ambient Mode**: If paused for more than 5 minutes, trigger a dark, drifting screensaver to prevent OLED burn-in.
- **Instant Scrubbing**: Left/Right on the D-pad instantly seeks 10 seconds backward or forward without invoking the full OSD overlay.
- **Advanced Quick Menu**: Pressing Down opens a bottom sheet layout to:
  - Toggle local `.srt` files or embedded subtitle tracks.
  - Search and download subtitles directly from OpenSubtitles.
  - Select Audio Tracks, preferring passthrough for surround sound receivers.

## 6. Onboarding, Permissions & Settings
- **Onboarding**: A clean first-launch screen requesting `READ_MEDIA_VIDEO` (or `MANAGE_EXTERNAL_STORAGE` depending on targeted API) with TV-friendly dialogs. It must feature a brief, visual overlay pointing out where the sidebar menu and content rows are located so the user knows exactly how to navigate.
- **Settings Screen**: A standardized Material 3 list containing:
  - Language Override (App UI language vs. Default Audio/Sub language selection).
  - Force Rescan Local Files.
  - Clear Image Cache.
  - Subtitle Appearance (Size, Color, Background Opacity).
  - Open Source Licenses (properly attributing referenced projects like Nova Video Player and laposa).
  - A focusable button at the very bottom: "Donate to XDanfr on Github".
- **Assets**: Scaffold manifest configurations and build directories for a 1:1 `ic_launcher` and a 320x180 `ic_banner` for the Android TV Home screen launcher.

## Implementation Steps
**Total Duration:** 70h 35m 43s

### Task_1_Infrastructure: Set up core infrastructure: Add Android TV Compose dependencies (androidx.tv:tv-material), configure Hilt for DI, define Room entities for media library and playback progress, and implement Retrofit services for TMDB and OpenSubtitles. Implement a lightweight and resource-efficient local file scanning architecture mimicking patterns from Nova Video Player. Include the regex-based filename parser.
- **Status:** COMPLETED
- **Updates:** - Hilt, Room, and Retrofit services (TMDB/OpenSubtitles) are configured.
- **Acceptance Criteria:**
  - Project builds with TV Compose dependencies
  - Hilt modules and Room schema (Media/Progress) are functional
  - TMDB API_KEY integrated and Retrofit services working
  - Regex parser correctly cleans filenames
  - File scanning is efficient and doesn't bloat background resources
- **Duration:** 10h 6m 37s

### Task_2_Library_Navigation: Build the cinematic library UI: Implement the Persistent Navigation Drawer with focus memory, the Home screen featuring a FeaturedCarousel with glassmorphic scrims, and the Search grid. Integrate TMDB metadata enrichment with Coil. Implement 'Home screen soft refresh' logic.
- **Status:** COMPLETED
- **Updates:** - FeaturedCarousel with glassmorphic scrims and auto-scroll is implemented.
- **Acceptance Criteria:**
  - Navigation drawer handles focus memory and back-button snap
  - FeaturedCarousel displays highlighted content with cinematic gradients
  - Search UI displays dynamic grid results
  - Home screen content shuffles slightly on return while row order stays identical
  - The implemented UI must match the design provided in input_images/image_0.png and input_images/image_1.png
- **Duration:** 10h 8m 56s

### Task_3_Playback_Engine: Implement the Media3 playback engine: Integrate ExoPlayer for local files, build the auto-hiding Player OSD with 10s D-pad scrubbing, and create the Quick Menu for subtitle/audio selection. Sync playback state with Room for 'Continue Watching'.
- **Status:** COMPLETED
- **Updates:** - Advanced Quick Menu (Bottom Sheet) implemented for audio/subtitle selection.
- **Acceptance Criteria:**
  - Media3 plays local files with hardware audio support
  - OSD auto-hides after 3s and handles 10s scrubbing
  - Quick Menu allows track selection and OpenSubtitles download
  - Playback position persists in Room
  - The implemented UI must match the design provided in input_images/image_3.png
- **Duration:** 10h 2m 17s

### Task_4_Polish_Onboarding_Settings: Add advanced features and premium polish: Implement the Media Details slide-out modal, first-launch onboarding (permissions + UI pointers), Ambient Mode screensaver, and a comprehensive Settings screen. Apply 1.05x focus scaling and 0.95x click-bounce physics. Integrate TV assets and perform final verification.
- **Status:** COMPLETED
- **Updates:** - 0.95x click-bounce physics implemented on all clickable media components.
- Settings screen completed with: Language selection, Force Rescan, Clear Cache, Subtitle Appearance, Open Source Licenses, and 'Donate' button.
- Onboarding screen enhanced with visual UI pointers (overlays).
- Ambient Mode screensaver (5-min inactivity) is fully functional.
- Media Details slide-out modal is verified against reference images.
- Adaptive icon and TV banner are correctly integrated.
- **Acceptance Criteria:**
  - Media Details modal and Onboarding overlay are functional
  - Ambient Mode activates after 5-min inactivity
  - Focus (1.05x) and click-bounce (0.95x) physics implemented
  - Settings includes: Language selection, Clear Cache, Open Source Licenses page, and focusable 'Donate to XDanfr on Github' button
  - Adaptive icon and TV banner are present
  - The implemented UI must match the design provided in input_images/image_2.png
  - App builds and runs without crashes, all existing tests pass, and verified by critic
- **Duration:** 40h 17m 53s

