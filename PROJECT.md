# METABOL 🧬

Offline-first Android health data journal, written in Kotlin and Jetpack Compose.

## Current status
Early **prototype**: premium dark dashboard and illustrative weight chart. The weight points are sample data, not persisted user measurements. Interactive chart controls, nutrition entry, Room persistence, and adaptive analysis are planned, **not implemented**.

## Build
GitHub Actions builds a debug APK on pushes and pull requests. Open the **Actions** tab and download the `METABOL-debug-apk` artifact from a successful run. The build has not yet been verified.

Local requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle :app:assembleDebug
```

## Privacy & scientific scope
No network permissions. The prototype does not diagnose disease or calculate body fat from weight alone. Future estimates will expose assumptions and uncertainty.

Developed by Deniz.
