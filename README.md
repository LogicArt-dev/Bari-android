# Bari Android

The Android application for Bari, built with Kotlin and Jetpack Compose.

## Requirements

- JDK 17
- Android SDK Platform 36 and Android SDK Build-Tools

The Gradle wrapper downloads Gradle 8.13 automatically. Android Gradle Plugin
8.13.2 is used by the project.

## Build

```shell
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Run the configured unit-test task with:

```shell
./gradlew test
```

## Project notes

- The application module is `app`; its namespace and application ID are
  `com.logicart.bari`.
- `minSdk` is provisionally set to 26 and must be reviewed before release.
- Android SDK location is provided locally through `local.properties` or the
  `ANDROID_HOME` environment variable. `local.properties` is not committed.
