# THE ONE — Hyper-Realistic Media Deck

**ÞE ĀN** (Old English styling)

A full-screen Android app wrapping the Hyper-Realistic Media Deck with native **System Media Controls** (notification / lock-screen player matching Android's MediaStyle).

## Features
- Full immersive WebView experience of the original media deck
- Android MediaSession integration → system media controls appear in the notification shade and on lock screen (play / pause / next / prev / seek)
- App icon: classic He-Man artwork
- App name: **THE ONE**
- Dark cyber/fantasy aesthetic matching the original deck

## Build the APK (GitHub Actions recommended)

1. Push this repository to GitHub.
2. Go to **Actions** tab → run the **Build APK** workflow.
3. Download the signed or debug APK from the workflow artifacts.

### Local build (requires Android SDK)

```bash
# Install Android command-line tools + SDK 34 + build-tools
# Then:
export ANDROID_HOME=/path/to/sdk
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Using the System Media Controls

The WebView exposes a JavaScript bridge `AndroidMedia`:

```js
// From the media deck JS (or injected):
AndroidMedia.updateMetadata("Track Title", "Artist", durationMs, base64ArtOrNull);
AndroidMedia.setPlaying(true, currentPositionMs);
AndroidMedia.seek(positionMs);
```

When the user taps system controls, the app calls back into the page via:

```js
window.AndroidMediaBridge = {
  play() { /* your player.play() */ },
  pause() { /* ... */ },
  next()  { /* ... */ },
  prev()  { /* ... */ },
  seekTo(ms) { /* ... */ }
};
```

You can inject a small script into `index.html` (or the React root) to wire your existing audio/video element to these methods so the system controls stay in sync.

## Project structure
- `app/src/main/assets/index.html` — the original hyper-realistic media deck
- `app/src/main/java/.../MainActivity.java` — WebView + MediaSession + Notification
- Icons generated from the provided He-Man artwork

## License
The media deck assets remain under their original terms. App wrapper code is provided as-is for personal use.
