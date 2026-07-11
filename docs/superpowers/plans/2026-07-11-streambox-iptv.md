# StreamBox IPTV Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A single-APK Kotlin Android IPTV player ("StreamBox") for phones and Android TV that streams iptv-org playlists.

**Architecture:** Single `:app` module, MVVM + repositories, Hilt DI. Room caches streamed-parsed M3U channels; Media3 ExoPlayer (HLS + progressive) over an OkHttp data source plays streams. One Activity; runtime TV detection routes to tv-material screens vs material3 phone screens sharing ViewModels.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, androidx.tv:tv-material 1.0.0, Media3 1.5.1, Room 2.6.1 + Paging 3.3.5, Hilt 2.52 (KSP), OkHttp 4.12.0, Coil 2.7.0, DataStore 1.1.1.

## Global Constraints

- minSdk 23, targetSdk 35, compileSdk 35. JDK 21, Gradle 8.14 (wrapper).
- App name "StreamBox", package `com.streambox.app`.
- Default playlist: `https://iptv-org.github.io/iptv/index.m3u`; presets for `index.category.m3u`, `index.country.m3u`, `index.language.m3u`; custom URL support.
- Manifest MUST have: `LEANBACK_LAUNCHER` intent filter + normal launcher, `android:banner`, `android:usesCleartextTraffic="true"`, `uses-feature touchscreen required=false`, `uses-feature android.software.leanback required=false`.
- M3U parsing is streaming line-by-line, never whole-file-in-string; malformed lines skipped, never crash.
- ExoPlayer HTTP: browser-like User-Agent, cross-protocol redirects, ~15s connect/read timeouts.
- Every screen D-pad navigable; TV focus scale 1.05–1.1x with visible border/glow; dark theme default.
- R8 enabled for release with Media3/Room/Hilt keep rules; `./gradlew assembleRelease` must work immediately (debug-signing fallback), universal ABI.
- Player dead-stream error copy: "Stream unavailable — it may be offline or geo-blocked" with Retry + Next channel actions.
- About screen legal note: player only; streams publicly available links from the iptv-org project; hosts no content itself.

---

### Task 1: Project scaffolding (compiles empty app)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*` (via `gradle wrapper --gradle-version 8.14`), `.gitignore`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/streambox/app/StreamBoxApplication.kt` (`@HiltAndroidApp`)
- Create: `app/src/main/java/com/streambox/app/MainActivity.kt` (Compose "StreamBox" placeholder, `@AndroidEntryPoint`)
- Create: `app/src/main/res/values/strings.xml`, `values/themes.xml` (DayNight NoActionBar, black bg), launcher icon vector `res/drawable/ic_launcher_fg.xml` + adaptive icon, TV banner `res/drawable/banner.xml` (320x180 layer-list with app name), `res/xml/` none needed (cleartext via manifest flag)

**Interfaces:**
- Produces: version catalog aliases used by all tasks (`libs.androidx.room.runtime`, etc.); Hilt Application; single activity `MainActivity`.

Version catalog (`gradle/libs.versions.toml`) — exact:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.52"
room = "2.6.1"
media3 = "1.5.1"
composeBom = "2024.12.01"
tvMaterial = "1.0.0"
okhttp = "4.12.0"
coil = "2.7.0"
paging = "3.3.5"
datastore = "1.1.1"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
navigationCompose = "2.8.5"
hiltNavigationCompose = "1.2.0"
junit = "4.13.2"
kotlinxCoroutines = "1.9.0"
turbine = "1.2.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
tv-material = { group = "androidx.tv", name = "tv-material", version.ref = "tvMaterial" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
paging-runtime = { group = "androidx.paging", name = "paging-runtime-ktx", version.ref = "paging" }
paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
media3-datasource-okhttp = { group = "androidx.media3", name = "media3-datasource-okhttp", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

Manifest — exact:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <application
        android:name=".StreamBoxApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:banner="@drawable/banner"
        android:label="@string/app_name"
        android:theme="@style/Theme.StreamBox"
        android:usesCleartextTraffic="true"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode"
            android:theme="@style/Theme.StreamBox">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] Step 1: Write all gradle/config files, manifest, Application, MainActivity placeholder, resources.
- [ ] Step 2: `gradle wrapper --gradle-version 8.14`; Run: `./gradlew :app:assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] Step 3: Commit `chore: scaffold StreamBox Android project`.

### Task 2: Domain model + M3U parser (TDD)

**Files:**
- Create: `app/src/main/java/com/streambox/app/data/m3u/M3uParser.kt`, `.../m3u/ParsedChannel.kt`
- Test: `app/src/test/java/com/streambox/app/data/m3u/M3uParserTest.kt`

**Interfaces:**
- Produces: `data class ParsedChannel(name: String, url: String, tvgId: String?, logoUrl: String?, category: String?, country: String?)`;
  `class M3uParser { fun parse(lines: Sequence<String>): Sequence<ParsedChannel>; fun parseHeader(line: String): String? /* x-tvg-url|url-tvg */ }`
  Parser is pure Kotlin (no Android imports) so it runs as JVM unit test.
- Country derived from `tvg-id` suffix (e.g. `Channel.us` → "US") when present.

Attribute extraction regex: `Regex("([a-zA-Z0-9-]+)=\"(.*?)\"")` over the EXTINF line; display name = text after last comma outside quotes (practical rule: substring after final `",` or after first comma if no attrs). Skip: EXTINF without following URL line, URL lines without EXTINF, blank/comment lines. Never throw on garbage.

Test cases (write first, expect fail, then implement):

```kotlin
class M3uParserTest {
    private val parser = M3uParser()
    private fun parse(text: String) = parser.parse(text.lineSequence()).toList()

    @Test fun `parses well-formed entry`() {
        val out = parse("""
            #EXTM3U
            #EXTINF:-1 tvg-id="News.us" tvg-logo="http://x/l.png" group-title="News",Newsy
            http://example.com/stream.m3u8
        """.trimIndent())
        assertEquals(1, out.size)
        with(out[0]) {
            assertEquals("Newsy", name); assertEquals("News.us", tvgId)
            assertEquals("http://x/l.png", logoUrl); assertEquals("News", category)
            assertEquals("http://example.com/stream.m3u8", url); assertEquals("US", country)
        }
    }
    @Test fun `skips extinf without url and continues`() { /* 2 entries, middle missing url -> 2 parsed */ }
    @Test fun `handles missing attributes`() { /* name only, null tvgId/logo/category */ }
    @Test fun `handles comma inside quoted attribute`() { /* group-title="A, B",Name */ }
    @Test fun `skips garbage lines without crashing`() { /* binary junk, half lines */ }
    @Test fun `parses header tvg url`() { assertEquals("http://e/g.xml", parser.parseHeader("#EXTM3U x-tvg-url=\"http://e/g.xml\"")) }
    @Test fun `blank playlist yields empty`() { assertTrue(parse("").isEmpty()) }
}
```

- [ ] Step 1: Write failing tests. Run `./gradlew :app:testDebugUnitTest` → FAIL (unresolved M3uParser).
- [ ] Step 2: Implement `ParsedChannel` + `M3uParser`. Run tests → PASS.
- [ ] Step 3: Commit `feat: streaming M3U parser with malformed-line tolerance`.

### Task 3: Room database + settings

**Files:**
- Create: `app/src/main/java/com/streambox/app/data/db/ChannelEntity.kt` (table `channels`: `key` PK = MD5(url), name, url, tvgId, logoUrl, category, country, generation Long; indices on category, country, name)
- Create: `.../db/FavoriteEntity.kt` (table `favorites`: channelKey PK, addedAt), `.../db/RecentEntity.kt` (table `recents`: channelKey PK, playedAt), `.../db/ProgrammeEntity.kt` (table `programmes`: tvgId+start PK, stop, title)
- Create: `.../db/ChannelDao.kt`, `.../db/FavoriteDao.kt`, `.../db/RecentDao.kt`, `.../db/ProgrammeDao.kt`, `.../db/StreamBoxDatabase.kt`
- Create: `.../data/settings/SettingsRepository.kt` (DataStore: playlistUrl w/ default index.m3u, themeDark default true, epgEnabled)
- Create: `.../di/AppModule.kt` (DB, DAOs, OkHttpClient singleton w/ 15s timeouts + UA interceptor, DataStore)

**Interfaces:**
- Produces (used by Tasks 4–8):
  - `ChannelDao.pagingSource(query: String, category: String?, country: String?): PagingSource<Int, ChannelWithState>`
  - `ChannelDao.categories(): Flow<List<String>>`, `countries(): Flow<List<String>>`
  - `ChannelDao.channelsForCategory(category: String, limit: Int): Flow<List<ChannelWithState>>`
  - `ChannelDao.insertAll(items: List<ChannelEntity>)`, `deleteOtherGenerations(gen: Long)`, `count(): Int`
  - `ChannelDao.neighbor(...)` — next/prev by name within same filter (for zapping)
  - `FavoriteDao.toggle(key)`, `favorites(): Flow<List<ChannelWithState>>`, `isFavorite(key): Flow<Boolean>`
  - `RecentDao.touch(key, at)`, `recents(limit): Flow<List<ChannelWithState>>` (keep max 30, prune)
  - `ChannelWithState` = channel row + `isFavorite: Boolean` via LEFT JOIN
  - `SettingsRepository.playlistUrl: Flow<String>`, `setPlaylistUrl(String)`, `themeDark: Flow<Boolean>`, `setThemeDark(Boolean)`
  - `object PlaylistPresets { val presets: List<Preset(name, url)> }`
- User-Agent constant: `"Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"`.

- [ ] Step 1: Entities + DAOs + DB + DI module + SettingsRepository. Favorites/recents JOIN queries.
- [ ] Step 2: `./gradlew :app:assembleDebug` (KSP/Room schema validation passes at compile). Expected: BUILD SUCCESSFUL.
- [ ] Step 3: Commit `feat: Room cache, favorites/recents, settings datastore`.

### Task 4: PlaylistRepository — streaming import with generation swap (TDD on swap logic)

**Files:**
- Create: `app/src/main/java/com/streambox/app/data/PlaylistRepository.kt`
- Test: `app/src/test/java/com/streambox/app/data/PlaylistImportPlannerTest.kt`

**Interfaces:**
- Produces: `PlaylistRepository.refresh(): Flow<ImportProgress>` where `sealed class ImportProgress { data class Running(val count: Int); data class Done(val count: Int); data class Failed(val message: String) }`
- Implementation: OkHttp GET playlist URL → `response.body.source()` → `generateSequence { source.readUtf8Line() }` → `M3uParser.parse` → map to `ChannelEntity(generation = newGen)` → buffer 500 → `dao.insertAll` → emit Running(count) each batch → on success `deleteOtherGenerations(newGen)` + Done; on IOException emit Failed and delete rows of newGen (cache intact). Also stores parsed `x-tvg-url` into settings for EPG.
- Testable pure piece: batching/generation logic extracted or tested via fake DAO.

- [ ] Step 1: Failing unit test: fake DAO records calls; feeding N parsed channels emits batches of ≤500, success deletes other generations, simulated mid-stream IOException keeps old generation and removes new.
- [ ] Step 2: Implement; tests PASS.
- [ ] Step 3: Commit `feat: streaming playlist import with atomic generation swap`.

### Task 5: Player engine + screen

**Files:**
- Create: `app/src/main/java/com/streambox/app/player/PlayerManager.kt`
- Create: `.../player/ZapContext.kt` (serializable filter: category?/country?/query?/favoritesOnly/recentsOnly + current key)
- Create: `.../ui/player/PlayerViewModel.kt`, `.../ui/player/PlayerScreen.kt`, `.../ui/player/PlayerOverlay.kt`, `.../ui/player/ChannelBanner.kt`
- Modify: navigation graph (Task 6) route `player/{channelKey}?ctx=`

**Interfaces:**
- `PlayerManager(context, okHttpClient)`: builds ExoPlayer with `DefaultMediaSourceFactory(OkHttpDataSource.Factory(client).setUserAgent(UA))` — OkHttp client already has 15s timeouts + redirects (incl. cross-protocol via `followSslRedirects(true)`); `DefaultRenderersFactory` with extension renderer mode ON for TS compat. `fun play(url: String)`, `state: StateFlow<PlayerUiState>` where `sealed interface PlayerUiState { Buffering; Playing; Error(message) }`, `fun retry()`, `fun setResizeMode(mode)`, `release()`.
- `PlayerViewModel`: exposes `currentChannel: StateFlow<ChannelWithState>`, `zapNext()`, `zapPrev()` (DAO `neighbor` query within ZapContext, wraps around), `toggleFavorite()`, records recents on successful playback start.
- Screen behavior (both form factors): `AndroidView(PlayerView)` with `useController=false`; custom Compose overlay show on tap/OK, autohide 5s; DPAD_UP/DOWN intercepted at screen level for zapping with 3s channel banner (name+logo+category); error state per Global Constraints; long-press OK (KEYCODE_DPAD_CENTER long) or overlay button toggles favorite; aspect toggle cycles RESIZE_MODE_FIT/FILL/ZOOM.

- [ ] Step 1: Implement PlayerManager + ViewModel + screen + overlay + banner.
- [ ] Step 2: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. (Playback behavior verified on-device later; all failure paths mapped to Error state, no throw.)
- [ ] Step 3: Commit `feat: ExoPlayer engine with zapping, overlay, error recovery`.

### Task 6: Shared UI theme, navigation, ViewModels

**Files:**
- Create: `.../ui/theme/Theme.kt` (dark: background #0E0F13, surface #16181D, accent #7C4DFF vivid violet; light variant; Material3 + TV MaterialTheme wrapper), `.../ui/theme/Type.kt`
- Create: `.../ui/shared/ChannelCard.kt` (logo via Coil w/ tv-icon placeholder vector, name, category; phone + tv focusable variants w/ 1.08 scale + accent border glow on focus), `.../ui/shared/ErrorState.kt`, `.../ui/shared/LogoImage.kt`
- Create: `.../ui/home/HomeViewModel.kt` (rows: Continue Watching from recents, Favorites, then per-category limited lists; refresh trigger + ImportProgress state), `.../ui/browse/BrowseViewModel.kt` (Pager(pageSize=60) flow combining search query + category + country filters), `.../ui/search/SearchViewModel.kt`, `.../ui/settings/SettingsViewModel.kt`
- Create: `.../ui/AppNavHost.kt` (routes: home, browse, search, settings, about, player/{key}), `.../ui/DeviceType.kt` (`isTv(context)` via UiModeManager)
- Modify: `MainActivity.kt` — set content to themed NavHost, keep screen on during playback

**Interfaces:**
- Produces: route names consumed by phone+TV screens; `HomeViewModel.rows: StateFlow<List<HomeRow>>` (`data class HomeRow(title: String, channels: List<ChannelWithState>)`); `BrowseViewModel.channels: Flow<PagingData<ChannelWithState>>`, `setQuery/setCategory/setCountry`; `countryFlagEmoji(code: String): String` util.

- [ ] Step 1: Implement theme/components/viewmodels/nav.
- [ ] Step 2: Build passes; commit `feat: theme, shared components, navigation, viewmodels`.

### Task 7: Phone screens

**Files:**
- Create: `.../ui/phone/PhoneHomeScreen.kt` (LazyColumn of LazyRows, TopAppBar w/ search+settings icons, import progress banner w/ channel count)
- Create: `.../ui/phone/PhoneBrowseScreen.kt` (paging LazyColumn list items, category dropdown + country dropdown w/ flag emoji, search field)
- Create: `.../ui/phone/PhoneSearchScreen.kt`, `.../ui/phone/PhoneSettingsScreen.kt` (playlist URL text field + preset picker, theme toggle, clear cache, EPG toggle, About link incl. NSFW note), `.../ui/phone/AboutScreen.kt` (legal note)

- [ ] Step 1: Implement; wire into NavHost for non-TV.
- [ ] Step 2: Build passes; commit `feat: phone UI screens`.

### Task 8: TV screens (tv-material, D-pad)

**Files:**
- Create: `.../ui/tv/TvHomeScreen.kt` (tv-material `ImmersiveList`-style: vertical list of horizontal rows via `LazyColumn`+`LazyRow` of tv-material `Card`s, focus restoration via `focusRestorer`, nav drawer or top tab row: Home/All/Search/Settings)
- Create: `.../ui/tv/TvBrowseScreen.kt` (`LazyVerticalGrid` paged, side filter column for category/country, D-pad reachable)
- Create: `.../ui/tv/TvSearchScreen.kt` (on-screen QWERTY grid keyboard composable operable by D-pad + results row)
- Create: `.../ui/tv/TvSettingsScreen.kt`, reuse AboutScreen
- Modify: `AppNavHost.kt` — route to TV screens when `isTv`

**Interfaces:**
- Consumes same ViewModels as phone. Focus rules: every screen sets initial focus (`focusRequester` on first item), `focusRestorer()` on rows, player returns focus to originating card (nav back stack preserves state via `rememberSaveable` + paging state).

- [ ] Step 1: Implement; build passes.
- [ ] Step 2: Commit `feat: Android TV 10-foot UI with D-pad navigation`.

### Task 9: EPG (optional lazy layer)

**Files:**
- Create: `.../data/epg/XmltvParser.kt` (XmlPullParser streaming; only `programme` elements; keep window now-6h..now+12h), `.../data/epg/EpgRepository.kt` (fetch tvg url stored by Task 4, insert programmes, prune old; `nowPlaying(tvgId): Flow<String?>`)
- Test: `app/src/test/java/com/streambox/app/data/epg/XmltvParserTest.kt` (sample XMLTV: parses title/times, skips malformed, window filter)
- Modify: ChannelCard + ChannelBanner to show now-playing subtitle when present.

- [ ] Step 1: Failing parser test → implement → PASS.
- [ ] Step 2: Wire lazily (refresh after channel import completes, failures silent). Build; commit `feat: optional XMLTV now-playing EPG`.

### Task 10: Release build, R8 rules, README, final verification

**Files:**
- Modify: `app/build.gradle.kts` — release: `isMinifyEnabled=true`, `isShrinkResources=true`, signingConfig: use `keystore/streambox.jks` if present else debug keystore fallback (so assembleRelease always works)
- Modify: `app/proguard-rules.pro` — keep rules for Media3 (`-keep class androidx.media3.** {*;}` narrowed: `-dontwarn androidx.media3.**` + defaults usually suffice; add Room `-keep class * extends androidx.room.RoomDatabase`), OkHttp/Okio dontwarns
- Create: `README.md` — build commands, signing instructions, adb sideload steps (`adb connect <tv-ip>`, `adb install -r app-release.apk`), legal note

- [ ] Step 1: Configure release signing + R8 rules.
- [ ] Step 2: Run `./gradlew test` → all unit tests PASS. Run `./gradlew assembleRelease` → BUILD SUCCESSFUL, verify single universal APK at `app/build/outputs/apk/release/`.
- [ ] Step 3: Write README; final commit `feat: release build config and docs`.
