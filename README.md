# StreamBox

A modern IPTV player for Android phones **and** Android TV, built with Kotlin,
Jetpack Compose, Compose for TV, and Media3 ExoPlayer. One universal APK runs
on both form factors: touch UI on phones, a D-pad-driven 10-foot UI on TV.

- Default playlist: [iptv-org](https://github.com/iptv-org/iptv)
  `https://iptv-org.github.io/iptv/index.m3u` (~12,000+ channels), with
  presets for the category/country/language variants and support for any
  custom M3U URL.
- Channels are streamed line-by-line into a Room cache — cached channels show
  instantly on launch while the playlist refreshes in the background.
- HLS (`.m3u8`) and raw MPEG-TS/HTTP streams, favorites, recents ("Continue
  Watching"), instant local search, category/country filters, channel zapping
  with D-pad up/down, aspect-ratio toggle, and optional XMLTV "now playing".

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

## Signing (optional, for real distribution)

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/streambox.jks \
  -alias streambox -keyalg RSA -keysize 2048 -validity 10000
```

Then export matching credentials before building (defaults are `streambox`):

```bash
export STREAMBOX_STORE_PASSWORD=... STREAMBOX_KEY_ALIAS=streambox STREAMBOX_KEY_PASSWORD=...
./gradlew assembleRelease
```

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
