# Large-library browsing

## First local pass

The Movies grid already uses `LazyVerticalGrid`, and Coil's `AsyncImage` resolves image
dimensions from its displayed constraints. Neither path needs a full-library artwork
prefetch. The initial changes target data preparation rather than replacing those systems.

Before this change, `LibraryViewModel` sorted the full Movies result on its main-thread
scope even though `MediaDao` had already ordered it by title. Movies now follows Room's
`title COLLATE NOCASE` order directly. This intentionally makes SQLite's collation the
ordering authority, including for non-ASCII titles.

TV Shows previously sorted episodes on the ViewModel's main dispatcher, grouped shows
in composition, and regrouped seasons when an episode row was composed. `LibraryContent`
now prepares show/season groups and representative episodes on `Dispatchers.Default`.
The screen consumes the prepared groups in both presentation modes.

Both streams start lazily on first use and remain subscribed until the ViewModel clears.
Reopening after more than five seconds no longer restarts the observer. Metadata edits,
favourites and hidden titles still arrive through Room while the page is closed. The
tradeoff is retaining a live observer and its current snapshot after the first visit;
this is not a bounded-memory or paged implementation. Equal query results are discarded
before show preparation, with the comparison also running off the main thread.

## Local regression coverage

`LibraryContentTest` covers a 1,200-movie snapshot, reopening after ten seconds, hidden
title removal and artwork refresh while closed, lazy query startup, cancellation when
the owning scope ends, and a 10,000-episode fixture. The episode cases check title,
season and episode ordering, unknown episode numbers, representative selection,
favourite/artwork updates and removal of a whole show.

These are flow/preparation tests. They do not measure Room query latency, Android TV
rendering, image downloads, decoder memory or hardware frame times.

## Next measurements

Use a release build on the same TV and library before and after this change, including
at least 900 movies. Keep the device, storage, artwork-cache state and animation settings
the same. Record cold first entry separately from reopening after more than five seconds.

Capture a system trace during first entry, reopening and sustained D-pad scrolling.
Compare time to the first focusable card, frame deadlines, Room work, main-thread work
and image decode/load activity. Repeat with cached artwork and with missing artwork.
Also check returning from playback, opening a context menu, hiding a title, metadata
refresh and both TV Shows presentation modes.

If first-query latency or retained snapshot memory is still material, evaluate a Room
PagingSource and title-order index with measured query plans. If scrolling is dominated
by image work or entry animations, profile those separately before changing request
concurrency, cache budgets or motion. No FPS or speedup is claimed from the local tests.
