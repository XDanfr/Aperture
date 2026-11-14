# Phone companion

Aperture Companion (`:mobile`, application ID `cc.xdan.aperture`, shared with the TV app) is a phone app that pairs with Aperture on a TV. With it you can:

- browse the TV's library
- start playback on the TV
- control playback from the phone

It is built as its own APK and bundle, published on the same Play listing as the TV app. The TV build requires leanback and the companion requires a touchscreen, so each device only ever gets one of them. The TV build is served from Play's Android TV track and the companion from the phone tracks; see `docs/PLAY_RELEASE.md`.

## Modules

| Module | Contents |
| --- | --- |
| `:core` | Code shared by both apps: theme palettes and colour tokens, motion/shape/spacing/elevation tokens, the Outfit font, the brand-mark artwork, and the companion wire protocol (`companion/protocol`). Files keep their original packages. |
| `:app` | The TV app. The companion server lives in `companion/` and the pairing UI in `ui/screen/companion/`. |
| `:mobile` | The phone app, built with Material 3 Expressive and Compose. |

## Protocol

- **Transport.** Plain HTTP/1.1 on the LAN. The port is 47650, or the next free port up to 47657.
- **Discovery.** The TV advertises over NSD as `_aperture._tcp`. The TXT record carries `id` (a stable TV id) and `v`.
- **When it runs.** The TV runs the server only while Aperture is in the foreground (`MainActivity` `onStart`/`onStop`).
- **Pairing.** The TV shows a 6-digit code and a QR code (`aperture://pair?h=…&p=…&c=…&id=…&n=…`). Pairing exchanges the code for a bearer token.
  - The TV stores only a SHA-256 hash of the token.
  - Codes expire after 10 minutes and rotate after 5 wrong guesses.
  - When a phone picks the TV from its discovery list, it calls `/v1/pair/request`, which pops up the code on the TV.
- **Endpoints:**
  - `GET /v1/info`
  - `POST /v1/pair/request`
  - `POST /v1/pair`
  - `POST /v1/unpair`
  - `GET /v1/library`
  - `GET /v1/state`
  - `GET /v1/events` (Server-Sent Events of `TvState`)
  - `POST /v1/command`
  - `POST /v1/colors` (the phone's Material You accents)
- **Commands:**
  - play media
  - toggle
  - seek to / seek by
  - next episode
  - stop
  - set favourite
- **What travels.** Only metadata and control messages. The phone loads artwork from TMDB's image CDN, and video never leaves the TV.

## Android 17 local network protection

Apps targeting API 37 need `ACCESS_LOCAL_NETWORK`, in the Nearby devices permission group, for any LAN traffic, inbound or outbound.

- **TV:** requests the permission from the pairing dialog.
- **Phone:** requests it before scanning, discovery or manual pairing.
- **Play:** `scripts/check_play_manifest.py` allows the permission.

## Theming on the phone

The phone offers three kinds of theme:

- **Match TV** (default): follows the TV's theme. When the TV uses Artwork Dynamic, the phone uses Aperture Purple.
- **Material You**: wallpaper colours, light or dark with the system.
- **Aperture palettes**: the TV's named palettes, except Artwork Dynamic.

### Matching a phone on the TV

Each time the companion connects, it reports its Material You accents (`POST /v1/colors`, dark-theme roles). It connects only while it is open, so after a wallpaper or colour change the next return to the app sends the new colours. The TV stores the latest colours per paired phone.

Once a phone has reported colours, the TV's theme picker offers **Match phone**, previewed with the same three-arc swatch. With one such phone it applies straight away; with several, the TV asks which phone to match. A phone set to Match TV then uses Material You if the TV is matching it. Otherwise it uses the matched phone's colours, which `TvState.themeColors` carries.

## Launch animation

On Android 12+, the system splash shows a vector of the brand mark in the current theme's colours. On Android 13+, `MainActivity` selects the matching splash style with `SplashScreen.setSplashScreenTheme` whenever the theme changes. Material You uses Android 14's system role colours, so it follows the wallpaper.

When the app is ready, `LaunchOverlay` draws the identical mark at the splash icon's exact position, removes the splash, and plays the TV's launch sequence in reverse while the app fades in. If the splash showed different colours, the mark blends to the current theme (Android 12 always shows purple, and Match TV may have changed since the last launch).

Regenerate the splash vectors and styles with:

```bash
python3 tools/generate_companion_splash.py
```

Run it whenever a palette changes.

## Testing against the emulator

The Google TV emulator sits behind NAT at 10.0.2.15, so phones can't discover it. To pair a physical phone, forward the port and relay it onto the LAN:

```bash
adb -s emulator-5554 forward tcp:47660 tcp:47650
```

Then run any TCP relay from `0.0.0.0:47650` to `127.0.0.1:47660` on the Mac. On the phone, use **Enter the TV's address** with the Mac's LAN IP.
