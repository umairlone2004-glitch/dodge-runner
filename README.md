# Dodge Runner

A tiny offline endless-runner game for Android, written in Java with a hand-drawn
`Canvas` renderer. No game engine, no third-party libraries, no internet permission,
no ads, no analytics. Just one Activity and one custom `View`.

**Tap to jump. Tap again in mid-air to double jump. Do not touch the red blocks.**

## Download and install (no Play Store needed)

1. Open **[DodgeRunner.apk](https://github.com/umairlone2004-glitch/dodge-runner/releases/latest/download/DodgeRunner.apk)**
   on your Android phone, or open the [latest release](https://github.com/umairlone2004-glitch/dodge-runner/releases/latest) page and tap the APK.
2. Open the downloaded file from your notifications or the Downloads folder.
3. Android will ask you to allow installs from that app — tap **Settings → Allow from this source**.
4. Tap **Install**, then **Open**.

Works on Android 5.0 (API 21) and newer. The APK is signed with the standard
Android debug key, which is fine for sideloading and testing but is not a Play
Store upload.

## How the APK gets built

Every push to `main` runs [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml),
which:

1. installs JDK 17, the Android SDK and Gradle 8.7 on a GitHub-hosted runner,
2. compiles the app with `gradle assembleDebug`,
3. uploads the APK as a workflow artifact, and
4. publishes it to the release tagged `latest`.

So you get a fresh, installable APK on GitHub's servers without needing Android
Studio on your own machine. Push a tag like `v1.1` to cut a versioned release.

## Build it yourself

With Android Studio: **File → Open**, pick this folder, let Gradle sync, then press Run.

From a terminal, with JDK 17 and an Android SDK installed:

```bash
gradle assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/java/com/umairlone/dodgerunner/
  MainActivity.java     fullscreen host, pause/resume wiring
  GameView.java         game loop, physics, collision, rendering, high score
app/src/main/res/       theme, strings, adaptive + PNG launcher icons
.github/workflows/      CI that builds and publishes the APK
```

## How the game works

* The frame loop runs on `Choreographer`, so it is capped at the display refresh
  rate and delta-timed, meaning it plays the same on 60 Hz and 120 Hz screens.
* Jump velocity and gravity are expressed as fractions of screen height, and
  speeds as fractions of screen width, so the difficulty is identical on a small
  phone and a tablet.
* Obstacle spacing is generated in *pixels of gap* rather than fixed seconds, so
  a fair jump window is always available even as the game speeds up.
* The high score is kept in `SharedPreferences` on the device.

## License

MIT — see [LICENSE](LICENSE).
