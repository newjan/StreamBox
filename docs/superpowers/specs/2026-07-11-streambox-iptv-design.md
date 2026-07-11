# StreamBox — Android IPTV Player (Phone + TV) — Design

Date: 2026-07-11
Status: Approved (user-provided spec is authoritative; this doc records decisions the spec left open)

## Summary

A Kotlin Android app, single installable APK, that plays IPTV channels from the
iptv-org public playlists (default `https://iptv-org.github.io/iptv/index.m3u`,
~12k channels) plus arbitrary user-supplied M3U URLs. Dual UI: touch phone app
and D-pad-driven Android TV 10-foot UI. Dark Material 3 by default.

The full functional spec was provided by the user and is treated as the
requirements document verbatim (screens, M3U parsing rules, TV manifest
requirements, networking gotchas, performance constraints, deliverables).
This design doc only records architecture and the decisions the spec left open.

## Decisions made here (not in the spec)

- **Single Gradle module** (`:app`). The app is one deliverable APK; multi-module
  adds ceremony without benefit at this size.
- **Version set** (chosen for mutual compatibility with Gradle 8.14 / JDK 21 /
  compileSdk 35, all installed locally):
  - AGP 8.7.3, Kotlin 2.0.21 (Compose compiler plugin), KSP 2.0.21-1.0.28
  - Hilt 2.52 (via KSP), Room 2.6.1 (+ room-paging), Paging 3.3.5
  - Media3 1.5.1 (exoplayer, exoplayer-hls, datasource-okhttp, ui)
  - Compose BOM 2024.12.01, androidx.tv:tv-material 1.0.0
  - OkHttp 4.12.0, Coil 2.7.0, DataStore-preferences 1.1.1
  - navigation-compose 2.8.5, hilt-navigation-compose 1.2.0
- **UI split strategy**: one Activity, Compose end-to-end. At startup detect TV
  via `UiModeManager` (UI_MODE_TYPE_TELEVISION) and route to TV composables
  (tv-material) vs phone composables (material3). Shared ViewModels, shared
  theme tokens, separate screen composables where interaction models differ
  (Home, Browse, Search); Player and Settings largely shared with focus/touch
  handling layered on.
- **Settings storage**: DataStore preferences (playlist URL, active preset,
  theme). Room stores channels/favorites/recents only.
- **Playlist refresh model**: `PlaylistRepository.refresh()` streams the M3U via
  OkHttp `BufferedSource.readUtf8Line()` into the parser, inserts into Room in
  batches (~500) inside a transaction per batch, then swaps: parse into a
  staging generation column and delete rows of the old generation on success,
  so a failed refresh never wipes the cache. Favorites/recents live in separate
  tables keyed by a stable channel key (url hash) so they survive refreshes.
- **Paging**: Room `PagingSource` + paging-compose for All Channels; Home rows
  are limited queries (e.g. top N per category via separate keyed queries),
  category list from `SELECT DISTINCT group_title`.
- **Zapping order**: previous/next channel follows the currently browsed context
  (the query the user launched playback from: category/search/all), passed to
  the player as a filter descriptor; player pages through the same Room query.
- **EPG (nice-to-have)**: implemented as optional lazy layer — parse `url-tvg`
  header attr, fetch XMLTV with a streaming XmlPullParser, keep only "now/next"
  per tvg-id in memory-bounded map, persisted to Room table `programmes` with
  time-window pruning. Never blocks channel list; failures silent.
- **Signing**: release build uses a checked-in debug-style keystore generated at
  first build via instructions (or falls back to debug signing config) so
  `./gradlew assembleRelease` works out of the box; README documents replacing
  with a real keystore.
- **App icon/banner**: generated simple vector/PNG placeholder (320x180 banner).

## Architecture

MVVM + repository. Hilt DI. Layers:

- `data/` — Room (entities, DAOs, DB), OkHttp module, `M3uParser`
  (pure Kotlin, line-based, unit-tested), `PlaylistRepository`,
  `FavoritesRepository`, `RecentsRepository`, `EpgRepository`, `SettingsRepository`.
- `player/` — `PlayerManager` wrapping ExoPlayer: OkHttp data source with
  browser UA, cross-protocol redirects, 15s timeouts; error mapping to
  user-facing state; aspect-ratio modes.
- `ui/` — `theme/`, `phone/` screens, `tv/` screens, `shared/` components
  (channel card, logo with placeholder, error state), `MainViewModel` +
  per-screen ViewModels.

Error handling: parser skips malformed entries; network failures surface as
non-blocking snackbars/banners with cached data still shown; player errors show
the spec'd error state with Retry / Next channel; no uncaught exceptions from
dead streams.

Testing: JVM unit tests for `M3uParser` (well-formed, malformed, missing attrs,
huge-line, quoting variants), plus repository-level test for generation swap.

## Success criteria

`./gradlew assembleRelease` produces one universal APK that installs on phone
(touch) and Android TV (LEANBACK_LAUNCHER, D-pad-complete), lists ~12k channels
smoothly, plays HLS and raw TS/HTTP streams, and survives dead streams.
