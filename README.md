# dhoon

A small personal music player for Android, by [@rosh4n.mad](https://github.com/uniqueroshu). It is a WebView app: the whole UI is one HTML file (`assets/www/index.html`) wrapped by a thin Java shell (`src/`).

**Download:** the signed APK is on the [Releases page](../../releases/latest). Android 7.0+ (API 24). Package `com.roshan.dhoon`.

## What it plays (free and legal sources only)

- **Audius** - artists' own uploads, streamed through Audius' public API.
- **Jamendo** - Creative Commons tracks, through Jamendo's public API. Attribution links are shown in the app.
- **Internet Archive** - old recordings (up to 1964) hosted on archive.org. The app only links to files archive.org serves; it does not host them.
- **YouTube** - only through YouTube's official embedded player, visible on screen. No stream extraction, no ad blocking, no background or audio-only playback. Search results may come from the YouTube Data API (your own key) or public Piped/Invidious search endpoints; those are used for titles and video IDs only.
- **Apple / Deezer** - 30-second preview clips from their public search APIs, with links back to the source.

There is no Spotify, JioSaavn or other catalog ripping.

Rights stay with the original artists and labels. If you are a rights holder and want a link removed, open an issue.

## Features

Home with old-gold shelves, search with history, favorites and playlists, offline saving for sources that allow it (offline playlists are created automatically), queue, swipe-down player, liquid-glass UI tuned for mid-range phones.

## Build

You need the Android SDK (build-tools 33, platform 33) and a JDK 17. No Gradle:

```
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/a.apk -I android.jar --manifest AndroidManifest.xml -A assets \
  --rename-manifest-package com.roshan.dhoon --min-sdk-version 24 --target-sdk-version 34 \
  --java build/gen build/res.zip
javac -source 8 -target 8 -cp android.jar -d build/cls $(find src build/gen -name '*.java')
d8 --min-api 21 --lib android.jar --output build $(find build/cls -name '*.class')
zip -j build/a.apk build/classes.dex
zipalign -f 4 build/a.apk build/u.apk
apksigner sign --ks your.keystore --out dhoon.apk build/u.apk
```

Sign with your own key. The release APK is signed with the author's key, so builds signed with a different key will not install over it.

## Licence

No licence is granted yet: all rights reserved by the author until one is added.
