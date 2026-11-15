# Jellyfin support plan

Working plan for adding Jellyfin to Aperture. Kept in the repo so it survives context resets.
Remove it before `feat/jellyfin` is merged into `master`.

## Workflow
- Integration branch: `feat/jellyfin` (from `master`).
- Each step gets its own branch from `feat/jellyfin` (e.g. `feat/jellyfin-profiles`) and a PR into `feat/jellyfin`.
- Finish and test each step before starting the next. Tick the step off here in the same PR.
- Testing: native on the Mac (`./gradlew :shared:jvmTest`, desktop app), plus the Android TV emulator, CI and other platforms where needed.
- Commits: XDan is the sole author, so no Co-Authored-By trailers. PR bodies have no Claude footer.

## Decisions
- **Server:** Jellyfin **12.0+** only, using jellyfin-sdk-kotlin 1.9.x in `:shared` commonMain (both targets run on the JVM).
- **Profiles:** Aperture always has profiles, and they're added to base Aperture first. A profile can link to one Jellyfin user on one server. The schema allows more servers later.
- **Out of scope for now:** Live TV, music and books. Don't build them and don't mention them in docs or UI, but don't design them out either.
- **Watched-state sync** between matched local and Jellyfin titles is on by default.

## Research notes
- Jellyfin 12.0 turns off the old login methods (use the `Authorization: MediaBrowser ...` header via the SDK) and removes `/emby/*` and `GET /QuickConnect/Initiate` (it's now a POST). `GetItems` applies `recursive` when filters are used with `includeItemTypes`. Multiple versions now work for episodes, and watched state is shared across versions. Name sorting uses `SortName`.
- SDK: `createJellyfin { clientInfo; context (Android only) }`, `discovery.discoverLocalServers()`, `userApi.authenticateUserByName`, `quickConnectApi`, `webSocket.subscribe...`, `mediaSegmentsApi`, `trickplayApi`, `mediaInfoApi.getPostedPlaybackInfo`, `playStateApi` for reporting.
- Media segments (`/MediaSegments/{itemId}`) are filled by Intro Skipper, TheIntroDB and chapter-based plugins. One client feature covers all of them.
- Seerr 3.x (the merged Overseerr and Jellyseerr): `/api/v1/discover/{trending,movies,tv,movies/upcoming,tv/upcoming,genreslider/movie,genreslider/tv}`, `POST /api/v1/request {mediaType, mediaId, seasons?, is4k?}`, and `mediaInfo.status` (2 pending, 3 processing, 4 partly available, 5 available). Sign-in: `/auth/jellyfin/quickconnect/{initiate,check,authenticate}`, `/auth/jellyfin`, or the `X-Api-Key` header. It uses cookie sessions (`connect.sid`).
- Home Screen Sections plugin: `GET /HomeScreen/Sections`, `GET /HomeScreen/Section/{type}`. The response shape is undocumented, so check it on a live server.
- Non-admin users can't list plugins. Detect a plugin by calling its endpoint and treating a 404 as "not installed".
- Jellyfin Enhanced and Streamyfin plugins are mostly for the web or Streamyfin. Their server-side features (hidden content, spoiler guard) work without client code.
- Playback Reporting, Trakt and similar plugins work for free once playback is reported properly.

## Steps

### 0. Profiles (base Aperture) — [ ]
- `profile` table: id, name, avatar colour/initial, created/last-used, optional PIN later. Profile 1 is created by migration from existing data.
- Per profile: favourites (My List), hidden items, playback progress, Continue Watching, theme, subtitle appearance, and later the sidebar/home layout and Jellyfin link. The local library itself (files, metadata, artwork) is shared.
- Database 8→9: move `isFavorite`/`favoriteAddedAt`/`isHidden` from `media` to `profile_media_state(profileId, mediaId)`, and key `playback_progress` by `(profileId, mediaId)`. Add a migration test.
- Who's watching picker at launch when there's more than one profile. Fully usable with the D-pad. Add, rename and remove in Settings › Profiles. Switch from the sidebar or Settings.
- Companion: `TvState` carries the active profile. Commands act on the active profile.

### 1. Groundwork — [ ]
- `MediaKey` (local id | server + item id) replaces `mediaId: Long` in navigation, view models, progress and actions.
- Library mode for each profile: Local / Jellyfin / Hybrid.
- Sources: the existing local library becomes one source, `JellyfinSource` sits beside it, and `CatalogRepository` combines them.
- Tables `jellyfin_server` and `remote_item` (index: name, sort name, year, TMDB/IMDb/TVDB ids, series/season/episode, date added, rating, runtime, image tags, watched state, version count). Incremental sync: page through once, then follow WebSocket library changes.
- Tokens are stored privately and excluded from backups.

### 2. Connect and browse — [ ]
- Sign-in: find servers on the local network or type an address. Quick Connect first, password as fallback. The phone can help sign in.
- Sidebar: server libraries (`/UserViews`) with icons for each type. Collections and playlists are optional. Home is always first and Settings always last.
- Home rows: Continue Watching (Resume), Next Up, Latest for each library.
- Large libraries: sort (title, date added, release, rating, runtime, last played, random, asc/desc), filters (unwatched, favourites, genre, decade, 4K/HDR, source), an A–Z rail reachable with the D-pad, server-side paging, and each library remembers its sort. Local libraries get the same sort and filter.
- Info section in the details panel: local path or server/library/path, container, size, resolution/HDR, codecs, bitrate, audio and subtitle tracks, date added.

### 3. Playback — [ ]
- PlaybackInfo with a device profile for each player (ExoPlayer with the FFmpeg extension; mpv). Direct play, else direct stream, else HLS transcode. `setMedia` takes headers.
- Report play, progress, pause/seek and stop. The stats overlay shows the play method and transcode reason.
- Server audio and subtitle streams, and subtitle search through the server for Jellyfin items.
- Trickplay previews. Skip intro/credits/recap/preview from `/MediaSegments`, with auto-skip per type. Next episode from server Next Up and the credits marker.
- Optional cinema intros (`/Items/{id}/Intros`), off by default.

### 4. Hybrid — [ ]
- Matching: movies by TMDB then IMDb id; shows by TMDB/TVDB id; episodes by show, season and episode number. Jellyfin's own versions join the group.
- One card per title. A version picker on Play when there's more than one: source badge, resolution, HDR, codecs, size, path. Default: Ask / Prefer local / Prefer Jellyfin / Highest quality. "Choose version…" in the context menu.
- The top of the details panel shows where the content is ("On this TV · On Jellyfin (Name)").
- Mirror watched state between matched local and server items (on by default).

### 5. Editing by source — part of steps 2–4
| Action | Local | Jellyfin |
|---|---|---|
| My List | local favourite | server Favourites |
| Watched/unwatched | local | server watched state |
| Remove from Continue Watching | local | server where supported, else hidden in Aperture |
| Refresh assets / Choose artwork (TMDB) | kept | hidden; admins get Refresh metadata / Identify |
| Hide | kept | kept (stored in Aperture) |
| Media folders / rescan | kept | hidden in Jellyfin mode; admins get Scan server library |

Local items keep every option in every mode.

### 6. Sidebar and Home customisation — [ ]
- Settings › Sidebar: show/hide, reorder by picking an item up and moving it with up/down, add libraries/collections/playlists/genres/Discover/My Requests. In Hybrid, Movies and Shows either combine all sources or split into one entry per library.
- Settings › Home rows: reorder/hide, pin collections or genres, and use the server's Home Screen Sections when the plugin is present.

### 7. Discover (Seerr) — [ ]
- Seerr address plus Quick Connect sign-in (API key fallback). Trending, popular movies, popular shows, upcoming and genre rows. Titles you have link to the library by TMDB id. Status badges. Request with a season picker. A My Requests entry. Empty search results offer Request.

### 8. Phone companion v2 — [ ]
- String media keys and a source field. `TvInfo.protocolVersion` so old phones and new TVs still work together. Paged library and search instead of the full snapshot. Images through a TV-side `/v1/image` relay (no token on the phone). Libraries tab, version picker, skip buttons, Seerr requests, profile switching, sign-in help.

### 9. Polish — [ ]
- Desktop checks (mpv streaming with headers, local network discovery on the JVM), server admin actions, and a final D-pad pass on every new screen (focus memory, first item focused in dialogs, predictable Back).

## Remote control rules
All new UI must be fully usable with a D-pad: restore focus with `FocusMemory`, focus the first item when a dialog opens, handle Back predictably, and long-press/Menu opens the context menu. Test D-pad only on the Google TV emulator. See `docs/development/player-focus.md`.
