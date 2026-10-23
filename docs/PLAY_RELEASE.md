# Play distribution

`play`, `standard` and `creator` share application ID `cc.xdan.aperture`. They are alternative distributions, not side-by-side installations. Switching requires compatible signing certificates and a non-decreasing version code. Play App Signing may use a different certificate from the GitHub APK: do not promise seamless switching without checking this.

## Implemented boundaries

Play disables GitHub update checks, APK downloads/installation, sponsorship prompts, sponsorship links and verification. Its manifest removes install/delete permissions, the legacy-package query and update FileProvider. All distributions remove all-files access. GitHub sponsorship verification can be designed later alongside Play billing; no hidden unlock or purchase path is included now.

Shared fixes: foreground-only video, dedicated media keys, screen-on only during active playback, system screensaver allowed over Aperture's idle experience, metadata disclosure before video permission, in-app privacy pages, HTTPS-only subtitle API endpoints, rectangular TV banner, larger fallback launcher icons, version 1→2 database migration, no destructive database fallback, and exported Room schema.

Existing filesystem identities are retained for current library/history compatibility. MediaStore entries without a DATA path fall back to content URIs; selected document folders already use persisted grants. A complete conversion of existing paths to content URIs is intentionally not performed without an identity-preserving migration.

## Build and signing

Run `./gradlew :app:testPlayDebugUnitTest :app:bundlePlayRelease :app:assemblePlayRelease`, with ANDROID_HOME set to the installed SDK. Run `python3 scripts/check_play_manifest.py` after manifest generation.

The Build Play bundle workflow validates PRs with an unsigned release bundle. Manual dispatch requires repository secrets TMDB_API_KEY, OPENSUBTITLES_API_KEY, PLAY_UPLOAD_KEYSTORE_BASE64, PLAY_UPLOAD_STORE_PASSWORD, PLAY_UPLOAD_KEY_ALIAS and PLAY_UPLOAD_KEY_PASSWORD. Use the Play upload key, not an arbitrary replacement certificate. Configure Play App Signing in Console. Local builds without signing environment variables are unsigned and cannot be submitted.

## External release gates — not completed by source changes

- Review and publish docs/PRIVACY.md at a stable public privacy-policy URL and enter it in Console. The in-app privacy pages describe current source behaviour; review wording against actual service retention, provider agreements and deployed build before publication.
- Complete Data safety for filename-derived TMDB queries, artwork requests, optional OpenSubtitles authentication/search/downloads, IP/network metadata and device backups. Evaluate user-initiated transfer/service-provider exemptions individually; do not declare blanket “no data collected” without review.
- Justify READ_MEDIA_VIDEO as core continuous video-library access and submit its declaration. Never restore MANAGE_EXTERNAL_STORAGE to solve a TV picker limitation.
- Add accurate 16:9 TV screenshots, content rating, target audience, ads declaration, support contact and TV distribution opt-in. Provide reviewer credentials for optional subtitle features and instructions for adding a small lawful sample video through device storage/USB or selected folders. No Aperture account is created by the app.
- Verify third-party licence obligations, including FFmpeg/NextLib GPL source and notices, fonts and artwork/metadata terms; a list of library names is not a complete licence fulfilment package.
- Inspect the signed AAB using bundletool/Play App Bundle Explorer. Check all delivered ABIs, ELF LOAD alignment and APK ZIP alignment; test a 16 KB system. Dependency ELF checks alone do not certify the final artifact.
- Test on 32-bit Android TV and 64-bit Google TV, low RAM hardware and a 16 KB environment. Test API 27 through current supported TV OS versions.
- With only five-way D-pad and Back: reach every settings row, slider, search keyboard, subtitle login, folder picker, dialog, library action and player control. Check focus restoration and repeated Back to Home.
- Verify 720p/1080p/4K rendering, overscan, long strings, large fonts, banner name/opacity/crop and actual launcher icons. Upscaled fallback artwork should eventually be replaced with source-resolution exports if visibly soft.
- Exercise denial, permanent denial, limited video grants, revoked folder grants, USB removal/reinsertion, deleted/unreadable files and unavailable document picker. No mouse may be required to recover.
- Test media play/pause keys, Home/app switching, sleep/wake, buffering, paused idle timeout, end-of-file, next episode, audio-device switching, offline/API failures and memory usage on low-RAM devices. Confirm system screensaver appears when idle.
- Test upgrades from each supported historical schema with real fixture databases; never clear user data to hide a migration failure. Source history establishes the added v1→2 column, but does not substitute for a full historical upgrade matrix.
- Set a fresh versionCode before submission, verify API keys are supplied, and run Play internal testing/pre-launch reports before production rollout.

Higher-tier features such as voice search, Cast Connect where applicable, baseline profiles and refresh-rate matching remain optimisation work rather than a claim of baseline release certification.

## Validation in this change

- Play debug compilation and 28 JVM tests passed.
- The new version 1→8 upgrade test passed on the Android TV API 36 emulator, checking retained library ID, favourite timestamp and playback position.
- Play debug installed and launched on that emulator. D-pad navigation opened/dismissed customisation and completed onboarding to Home; no startup AndroidRuntime exception was observed.
- Banner PNGs in all five density folders are fully opaque. The xhdpi banner is 320×180; fallback icons are 160×160 at xhdpi.
- These are focused checks, not the complete hardware, accessibility or Play review matrix above. No production publication or Console changes were made.

Final local validation: minified Play release AAB and APK built successfully; standard/creator debug compilation and Play instrumentation APK assembly passed. The final AAB contains 28 native ELF files across arm64-v8a, armeabi-v7a, x86 and x86_64, all passing 16 KB LOAD alignment. The release APK passes zipalign with 16 KB native alignment. Its packaged permissions match the Play allowlist. The minified DEX no longer contains the GitHub release-check or device-authorisation endpoints. Local release artifacts are unsigned because no Play upload key was supplied. The signing workflow has not been dispatched and Play-generated split APKs still require Console/bundletool validation.
