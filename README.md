# METABOL

**METABOL** is an offline-first Android body-weight, nutrition and body-composition journal written in Kotlin and Jetpack Compose.

> Development preview, not a medical device. Values entered from smart scales or nutrition labels may be estimates.

## Features

| Screen | Capabilities |
| --- | --- |
| Overview | Weight log, backdated entries, edit existing records, edit/delete, touch-selectable smooth/straight line chart, 7/30/90-day chart filters, personal weight goal |
| Nutrition | Daily calorie and macronutrient totals, searchable offline starter food catalog with gram-based portions, manual add/edit/delete meals, navigate between dates, seven-day calorie comparison, macro-derived calorie estimate |
| Analysis | Time-range filters, minimum/maximum, mean, total change, weekly rate, last-seven-measurements mean |
| Body | Body-fat %, muscle mass and waist circumference, edit/delete records, change since previous measurement, trend charts for fat/muscle/waist |
| Settings | Optional weight goal, offline JSON backup and restore, CSV export, privacy information |

### Data and privacy

The app stores its records locally in Android `SharedPreferences`; it does **not** have account sync or a server. Exported JSON backups and CSV files contain personal body and nutrition measurements; store them securely. Restoring a backup replaces the existing local records and currently requires restarting the app to refresh every screen.

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

## Offline food lookup (preview)

The app includes a **small built-in reference catalog** of common foods, with approximate values per 100 g. Search by name, choose a food, enter grams, and the app calculates kcal, protein, carbohydrate and fat before adding the entry. This works offline and requires no API key. **It is not a verified nutrition database**; numbers are approximate reference values and are not suitable for precise medical dietary management. Prepared food and packaged product values depend on brand, recipe, cooking fat, and serving size. Prefer the package label or manually corrected entries when available. A verified external database and barcode scanning are not implemented yet.

## Nutrition entry improvements

Recent foods are stored locally and appear first in the offline catalog. Portion presets (50/100/150/200 g) speed up logging; arbitrary gram values remain available. Meal history cards visualize the relative **macro-derived energy** split (protein/carbohydrate/fat), not the share of the entered label calories. Reference values remain estimates and can be corrected through manual meal editing.

## Opt-in product search (preview)

The food picker now offers **online product search** via Open Food Facts. Enter a food name or an 8–14 digit barcode number, then explicitly tap the online-search button. A network connection is required. The query/barcode is sent to the third-party Open Food Facts service; local weight measurements and meal history are **not** transmitted. The offline catalog remains available if search fails. Only entries with numeric per-100-g kcal, protein, carbohydrate and fat values are displayed. Product data is community-maintained and can be inaccurate: check the package label before saving. Barcode **camera scanning is not implemented**; barcode numbers can be entered manually.

## Full-screen food explorer and offline reuse

The food search is a full-screen Compose dialog with lazy-loaded lists for local and online results, explicit network search, progress/error states, and portion selection. Successfully logged online products are stored in a local library (up to 30) for later offline reuse and can be removed from that library. Previous-day meals can be copied into the selected day with confirmation; existing entries are preserved. Nutrition values are estimates and should be verified against labels, especially when ingredients or serving sizes vary.
