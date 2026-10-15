# METABOL

**METABOL** is an offline-first Android body-weight, nutrition and body-composition journal written in Kotlin and Jetpack Compose.

> Development preview, not a medical device. Values entered from smart scales or nutrition labels may be estimates.

## Features

| Screen | Capabilities |
| --- | --- |
| Overview | Weight log, backdated entries, edit existing records, edit/delete, touch-selectable smooth/straight line chart, 7/30/90-day chart filters, personal weight goal |
| Nutrition | Daily calorie and macronutrient totals, add/edit/delete meals, navigate between dates, seven-day calorie comparison, macro-derived calorie estimate |
| Analysis | Time-range filters, minimum/maximum, mean, total change, weekly rate, last-seven-measurements mean |
| Body | Body-fat %, muscle mass and waist circumference, edit/delete records, change since previous measurement, trend charts for fat/muscle/waist |
| Settings | Optional weight goal, offline JSON backup and restore, privacy information |

### Data and privacy

The app stores its records locally in Android `SharedPreferences`; it does **not** have account sync or a server. Exported JSON backups contain personal body and nutrition measurements; store them securely. Restoring a backup replaces the existing local records and currently requires restarting the app to refresh every screen.

### Calculations

- Weight change = last measurement − first measurement.
- Weekly average rate = total change × 7 / elapsed days. Shown only when measurements span at least seven days.
- Last-seven-measurements mean is an arithmetic mean of the seven most recent *measurements*, not a calendar-week average.
- Seven-day calorie bars show recorded intake, not a recommended calorie target.
- Smart-scale fat/muscle measurements can vary with hydration and measurement conditions.

### Build

Requirements: Android SDK 35, JDK 17, Gradle 8.9.

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

The `Android Debug APK` GitHub Actions workflow runs tests and assembles an APK on branch updates; superseded branch runs are cancelled. Download the `METABOL-debug-apk` artifact from a **successful** run.

### Project status

This is a preview. Current persistence uses JSON strings in preferences, not Room. There is no cloud backup, barcode scanner, verified food database, or medical-grade body composition measurement. UI and device testing are ongoing.

Developed by Deniz.
