# بارع Bari

Bari is a bilingual Android file manager built with a single Android application
module, Kotlin, Jetpack Compose, and Material 3.

## Toolchain

- Java 17
- Android Gradle Plugin 8.13.2
- Gradle wrapper 8.13
- Kotlin and Compose compiler plugin 2.3.21
- Android SDK 36 (minimum SDK 26)

Use the checked-in wrapper for Gradle tasks:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The app's storage browser uses Android's all-files access where supported and
user-selected Storage Access Framework trees as a complementary route.

