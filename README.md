# Fuel Math

Fuel Math is an offline-first Android app for tracking vehicle operating costs and preventative maintenance. The project is intentionally narrow: it aims to help a person manage personally owned vehicles, understand what maintenance is due next, and keep a clear local record of what the vehicle costs to run.

## Overview

The app is being built as a local maintenance checkbook rather than a generic fleet platform or cloud service. Its core job is to answer a few practical questions quickly:

- What is overdue right now?
- What is due soon?
- Which maintenance items still need baseline service history?
- What should I do next?
- What has this vehicle cost me to operate?

## Current App Shape

The repository currently contains a native Android app built with Kotlin, Android Views, and Material components. The present implementation includes:

- Multiple vehicles with vehicle-type-aware summaries
- Preventative maintenance items, categories, and service history
- Manual mileage-aware maintenance calculations
- Fuel logging for liquid-fuel vehicles
- Charging logging for EV and plug-in hybrid workflows
- Cost, efficiency, health score, and recommendation summaries
- Local JSON backup and restore
- Local-only persistence with no account requirement

Data is stored on-device in `SharedPreferences` as app-managed JSON through [`FuelRepository.kt`](/C:/Users/william/Desktop/git/FuelMath/app/src/main/java/org/archuser/fuelmath/FuelRepository.kt).

## Supported Vehicle Types

- Gasoline
- Diesel
- Hybrid
- Plug-in hybrid
- EV
- Motorcycle
- Other

Vehicle type affects which fields, labels, units, and log actions the app exposes. For example, EVs use charging terminology and battery capacity, while liquid-fuel vehicles use fuel terminology and tank capacity.

## Project Scope

This repo follows a narrow MVP direction:

- Offline-first local storage is the source of truth
- Maintenance history is preserved as logs
- Derived values are calculated from stored records
- The dashboard should prioritize urgent work before historical detail
- Archived or out-of-scope product ideas should not expand the app into a SaaS, marketplace, fleet manager, or spreadsheet replacement

If a feature does not support preventative maintenance, due-status calculation, mileage tracking, fuel or charging tracking, or operating cost summaries, it is probably outside the intended MVP.

## Repository Layout

- [`app/`](/C:/Users/william/Desktop/git/FuelMath/app): Android application module
- [`app/src/main/java/org/archuser/fuelmath/MainActivity.kt`](/C:/Users/william/Desktop/git/FuelMath/app/src/main/java/org/archuser/fuelmath/MainActivity.kt): Main UI flow
- [`app/src/main/java/org/archuser/fuelmath/FuelCalculator.kt`](/C:/Users/william/Desktop/git/FuelMath/app/src/main/java/org/archuser/fuelmath/FuelCalculator.kt): Calculation and summary logic
- [`app/src/main/java/org/archuser/fuelmath/Models.kt`](/C:/Users/william/Desktop/git/FuelMath/app/src/main/java/org/archuser/fuelmath/Models.kt): Core data models and enums
- [`app/src/main/java/org/archuser/fuelmath/FuelJsonCodec.kt`](/C:/Users/william/Desktop/git/FuelMath/app/src/main/java/org/archuser/fuelmath/FuelJsonCodec.kt): Local JSON serialization and migration logic
- [`app/src/test/java/org/archuser/fuelmath/FuelCalculatorTest.kt`](/C:/Users/william/Desktop/git/FuelMath/app/src/test/java/org/archuser/fuelmath/FuelCalculatorTest.kt): Unit tests for calculation-heavy behavior
- [`.forgejo/workflows/release-apk.yml`](/C:/Users/william/Desktop/git/FuelMath/.forgejo/workflows/release-apk.yml): Tagged release APK automation

## Build Requirements

- Android Studio or Android SDK command-line tools
- Java 11 or newer for local builds

Android configuration in [`app/build.gradle.kts`](/C:/Users/william/Desktop/git/FuelMath/app/build.gradle.kts):

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 26`
- `versionName = "v0.1.0"`

## Local Build

From the repository root:

```powershell
.\gradlew.bat assembleDebug
```

To run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

To build a release APK:

```powershell
.\gradlew.bat assembleRelease
```

## Release Signing

Release signing is enabled only when all of the following environment variables are present:

- `RELEASE_KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Without them, a release build can still run, but it remains unsigned.

## Release Automation

Tagged releases are built through Forgejo automation in [`.forgejo/workflows/release-apk.yml`](/C:/Users/william/Desktop/git/FuelMath/.forgejo/workflows/release-apk.yml).

Current workflow behavior:

- Runs on pushed tags matching `v*` or `V*`
- Builds a signed release APK when required secrets are configured
- Publishes the APK to Forgejo releases
- Optionally mirrors the release to GitHub when the matching GitHub variables and secrets are set

## Testing

The current test suite focuses on calculation-heavy logic, including:

- Fuel efficiency and EV efficiency calculations
- Maintenance due-state calculations
- Health score and recommendation ordering
- JSON schema migration and round-trip behavior
- Reminder snapshot generation
- Export formatting

This keeps core maintenance and cost logic testable outside the UI.

## Current Version

`v0.1.0`
