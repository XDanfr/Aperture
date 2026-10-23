# Aperture privacy policy

Updated 23 October 2026. Developer: XDanfr. Privacy questions and deletion requests concerning developer-held data can be raised through [the Aperture project](https://github.com/XDanfr/Aperture/issues). Do not post passwords, tokens or private media filenames in a public issue.

## Video library

Aperture accesses videos allowed through Android permissions and folders you select. It stores library entries, media locations, favourites, hidden items, playback progress and preferences in application storage. Aperture does not upload video files. Android permission settings let you revoke access. Removing a selected folder removes its library access; clearing application storage removes the local library and preferences.

## Metadata and artwork

Library preparation sends title searches derived from filenames, with available year or episode information, to The Movie Database (TMDB) to retrieve descriptions and artwork. Artwork requests also contact TMDB. These requests reveal network information, including your IP address, to the service. This automatic matching is explained before video access is requested. Do not grant access if you do not want automatic metadata matching.

## Optional subtitles

Signing into OpenSubtitles sends your username and password directly to that service over HTTPS. Aperture stores the username, session token, expiry and service address in private application preferences, not the password. The session preferences are excluded from Android backups. Subtitle searches, authentication and downloads contact OpenSubtitles. Sign out in subtitle settings to remove the stored session. Third-party accounts and their deletion are managed by their respective services. Aperture does not create an Aperture account.

## Distribution differences

The Google Play build does not check GitHub releases, download APK updates, solicit GitHub sponsorship or verify sponsorship. Google Play manages its distribution and updates.

Sideload builds contact GitHub for release checks and downloads requested by the user. Optional GitHub sponsorship verification uses GitHub device authorisation to check sponsorship and stores a verification flag. The access token used for that verification is held in memory rather than persisted by Aperture.

## Backups, retention and deletion

Local library data remains until removed or application storage is cleared. Android may back up library data and preferences according to device and account settings. Uninstalling or clearing local storage does not itself delete cloud backups; manage backups in Android or Google Account settings. Third-party services determine their own server retention and account practices. Aperture cannot delete data held by those services on your behalf.

## Security and analytics

Network services use HTTPS. Release builds disable HTTP request logging. Aperture has no advertising or analytics SDK configured and does not sell user data. Runtime permissions and persisted folder grants limit media access. Android's application sandbox protects local data; this does not imply all stored data is separately encrypted by Aperture.

This policy must be reviewed whenever data handling changes. The Play Console Data safety declaration must describe the shipped build and applicable third-party handling, rather than treating local playback as proof that no data leaves the device.
