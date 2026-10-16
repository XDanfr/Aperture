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
This workflow builds the cc.xdan.aperture application ID. The Kotlin namespace remains
me.xdan.aperture; it does not determine the installed package identity.

The runner uses JDK 21, Android SDK 37 and Build Tools 37.0.0 to match the project.
Passwords are supplied to apksigner through environment variables. No keystore is
uploaded as an artifact. API keys are embedded in the APK as in local builds, so
repository secrets do not make those keys inaccessible to APK users.

## Package migration in 0.5

The new signing key uses the application ID `cc.xdan.aperture`, so the APK can be installed
alongside `me.xdan.aperture` without a signature conflict. Install and open the new APK
before removing the old app.

On launch, the new app checks specifically for `me.xdan.aperture` using a manifest package
query. When present, the centred **Updating Aperture** dialog explains the data reset.
OK opens Android's uninstall confirmation for the old package; the user must select
Uninstall (or OK on some TVs) there. Cancel or Back in Aperture closes the new app. Cancelling Android's
confirmation returns to the update dialog. If the system screen cannot open, the dialog
explains how to remove the old package in Android Settings.

The app rechecks package presence on return rather than treating an activity result as
proof of removal. Both paths request video access and begin the onboarding scan.
Theme and Ambient customisation remain available while scanning and once it finishes;
the screen stays open until the user chooses **Get started** (no tutorial) or
**Learn Aperture** (explicitly opens the tutorial). This includes migrating users, who
can request the tutorial without it being shown automatically.

Existing folders on removable storage must be selected again. Settings,
My List, watch progress, stored permissions and account sign-ins are not copied from the
old app. Uninstalling the old app removes its private data; it does not remove the user's
media library files. The old release did not expose a cross-app database export, and the
new package cannot access its private database.

The built-in updater in older releases can install the differently named package, but
cleanup and setup begin only when the user opens the new Aperture. Release notes should
explicitly tell users to open the new app after installation.
