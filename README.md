# StreamBox

A modern IPTV player for Android phones **and** Android TV, built with Kotlin,
Jetpack Compose, Compose for TV, and Media3 ExoPlayer. One universal APK runs
on both form factors: touch UI on phones, a D-pad-driven 10-foot UI on TV.

## Features

- **~13,000 live channels** from the default [iptv-org](https://github.com/iptv-org/iptv)
  playlist (`https://iptv-org.github.io/iptv/index.m3u`), with presets for the
  category/country/language variants and support for any custom M3U URL.
- **In-player channel guide** — press MENU (or D-pad ←) while watching: a side
  panel slides over the live video with categories/countries, your lists, and
  a search field, so you can switch channels without leaving playback.
- **Custom channel lists** — long-press OK on the remote (or long-press the
  screen on phones) to file the current channel into your own lists; lists
  appear as Home rows and in the in-player guide, and survive playlist
  refreshes.
- **Auto-recovery** — a failed stream silently retries (configurable 5–60s,
  default 10s, Settings → Playback) before showing an error.
- **Channel health** — "Scan channels" probes the playlist in the background
  and a "hide non-working channels" toggle filters dead streams everywhere;
  playing any channel also updates its status automatically.
- **Instant startup** — channels stream line-by-line into a Room cache; cached
  channels show immediately while the playlist refreshes in the background.
- Browse by category/country with a grid/list **Groups** browser, instant
  local search, favorites, recents ("Continue Watching").
- **Full D-pad navigation** on TV, including channel zapping with up/down
  while watching; visible focus with scale + accent border on every screen.
- HLS (`.m3u8`) and raw MPEG-TS/HTTP streams; aspect-ratio toggle
  (fit/fill/zoom); optional XMLTV "now playing" programme info.
- Dark mode by default (light theme available), Material 3 design.
- Runs on Android 6.0+ — bundles current CA root certificates so HTTPS works
  on old TV boxes with outdated system certificate stores (plus an opt-in
  "trust all certificates" escape hatch for hopeless cases).

## Requirements

- JDK 17+ (project builds with 21)
- Android SDK with platform 35 (`ANDROID_HOME` set or `local.properties`)
- No Android Studio needed — the Gradle wrapper does everything

## Build

```bash
# Debug build
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk

# Unit tests (M3U parser, playlist import, XMLTV parser)
./gradlew test

# Release build (R8-minified universal APK)
./gradlew assembleRelease        # → app/build/outputs/apk/release/app-release.apk
```

`assembleRelease` works immediately: if `keystore/streambox.jks` does not
exist, the release build falls back to the debug keystore so you can sideload
right away.

## Signing

The build signs releases with `keystore/streambox.jks` when it exists (the
whole `keystore/` directory is gitignored — never commit it). Create one with:

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/streambox.jks \
  -alias streambox -keyalg RSA -keysize 2048 -validity 10000

cat > keystore/keystore.properties <<EOF
storePassword=<your store password>
keyAlias=streambox
keyPassword=<your key password>
EOF
```

Credentials are read from `keystore/keystore.properties`, then from the
`STREAMBOX_STORE_PASSWORD` / `STREAMBOX_KEY_ALIAS` / `STREAMBOX_KEY_PASSWORD`
environment variables. Without a keystore, release builds fall back to debug
signing so `./gradlew assembleRelease` always works.

**Back up `keystore/` somewhere safe** — updates must be signed with the same
key, or users have to uninstall/reinstall.

Note: installing a build signed with a different key over an existing install
requires uninstalling first (`adb uninstall com.streambox.app`).

## Sideload onto Android TV

1. On the TV/box: Settings → Device Preferences → About → tap **Build** 7x to
   enable Developer options, then enable **USB debugging** (and **Network
   debugging** if available).
2. From your computer (TV and computer on the same network):

```bash
adb connect <tv-ip-address>:5555   # confirm the prompt on the TV
adb install -r app/build/outputs/apk/release/app-release.apk
```

StreamBox appears in the TV launcher's app row (Leanback launcher entry with
banner). On phones, install the same APK: `adb install -r app-release.apk` or
copy it over and open it.

## Legal

StreamBox is a player only. It streams publicly available links from the
iptv-org project and hosts no content itself. See the in-app About screen.
