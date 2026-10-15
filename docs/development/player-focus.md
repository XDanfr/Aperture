# First-entry player focus investigation

## Scope and history

- August: the thin OSD and classic-control option were introduced in `a629f15`; player engine setup changed in `30502f5`.
- October: `13f1ed9` retained the browsing page underneath Player so returning from playback preserved the card and play menu.
- `003862a` added recovery for browsing pages whose initial focus target disappears during loading.
- Later player buffering/engine delays restored focus temporarily but did not stop the background entry from taking it back.

## Confirmed cause

Navigation 3 1.0.1's `rememberDecoratedNavEntries` constructs entries inside `remember(backStack.toList())`. Aperture filters Player out of the browsing backstack, so that list deliberately stays unchanged while Player opens. The cached entry content consequently retained the old `browsingPaused` value and focus-recovery callback.

The debug reproduction showed `page=home focus=ActiveParent paused=false` immediately after Play/Pause and the player root became inactive. The main window stayed focused. This repeated after each delayed player focus request; no native-view focus transfer was logged at those points.

`NavContent` now keeps current browsing suspension and focus eligibility in `rememberUpdatedState` outside the cached entry content. Player focus is requested when the active control or window focus changes, after attachment frames, rather than after buffering or decoder events.

## Verification

On the existing Television_1080p emulator, a diagnostic build reproduced the failure before the fix. After the fix:

- Cold first entry retained Play/Pause focus throughout buffering and playback.
- Down opened QuickMenu with Audio highlighted.
- Closing QuickMenu and reopening controls worked.
- Subsequent player entry and QuickMenu navigation worked.
- Automatic episode continuation crossed into the next season; returning from playback retained that episode in the play menu.
- Standard debug APK assembly passed.

Physical-TV confirmation is still required. Temporary diagnostic logging was removed after establishing the cause. Subtitle styling, playback-engine selection and the OSD design were not changed.
