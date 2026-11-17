# Seerr for Android

A native Android client for [Seerr](https://github.com/seerr-team/seerr), written in Kotlin with Jetpack Compose. It is an unofficial companion app and is not affiliated with the Seerr project.

**Status: early development.** The app doesn't do anything useful yet. See [PLAN.md](PLAN.md) for what is being built and in what order.

## Build

You need Android Studio (2026.2 or later), which brings the JDK and Android SDK.

```sh
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # unit tests
./gradlew installDebug         # install on a running emulator or connected device
```

From a terminal, point `JAVA_HOME` at Android Studio's bundled JDK (`Android Studio.app/Contents/jbr/Contents/Home`) if you have no other JDK installed.

## Requirements

- Android 8.0 (API 26) or later.
- A Seerr server you can sign in to.
