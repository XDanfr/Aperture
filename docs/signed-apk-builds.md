# Signed APK builds

Merge the workflow into master, then open Actions → Build signed APKs → Run workflow.
Both standard and creator release APKs are built, signed, verified, and uploaded together
as a downloadable artifact (retained for 30 days). This does not publish a GitHub release.

## Repository secrets

In Settings → Secrets and variables → Actions, add:

| Secret | Value |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | Base64-encoded contents of your existing release JKS |
| ANDROID_KEYSTORE_PASSWORD | Keystore password |
| ANDROID_KEY_ALIAS | Signing key alias |
| ANDROID_KEY_PASSWORD | Key password (repeat the keystore password if identical) |
| TMDB_API_KEY | TMDB API key |
| OPENSUBTITLES_API_KEY | OpenSubtitles API key |

Encode the JKS locally; do not commit it or its encoded contents.

Linux:
```bash
base64 -w 0 aperture.jks > aperture.jks.base64
```

macOS:
```bash
base64 -i aperture.jks -o aperture.jks.base64
```

Copy the generated text into ANDROID_KEYSTORE_BASE64, then remove the temporary text file.
Keep a separate secure backup of the JKS and passwords. Base64 is encoding, not encryption.
GitHub secrets are limited to 48 KB; this approach is for a small JKS.

Use the same signing key for subsequent updates. Changing the package name alone does
not transfer settings, library permissions, or watch history to the new installation.
This workflow currently builds the existing me.xdan.aperture application ID.

The runner uses JDK 21, Android SDK 37 and Build Tools 37.0.0 to match the project.
Passwords are supplied to apksigner through environment variables. No keystore is
uploaded as an artifact. API keys are embedded in the APK as in local builds, so
repository secrets do not make those keys inaccessible to APK users.

## Future package migration (requested by Dan, 8 October 2026)

Deferred; do not implement as part of this CI change.

- Migrate the application ID to cc.xdan.aperture.
- On launch, detect whether me.xdan.aperture is installed.
- Show: “Press uninstall on the next screen to continue the update”.
- Provide OK and Cancel buttons.
- OK opens Android's system uninstall screen for the old package; uninstall remains
  a user-confirmed system action.
- Decide how cancellation and returning without uninstalling should behave before implementation.
- Consider preserving/exporting user data before removing the old app, as uninstalling deletes it.
