# Fuel Math

Fuel Math is a free Android maintenance log for the vehicles and equipment you own. It helps you keep track of what needs service next, what is overdue, and what each asset costs to operate.

It is built for personal use: cars, trucks, motorcycles, trailers, mowers, generators, skid loaders, chainsaws, golf carts, and other equipment. There is no account to create and no cloud service to depend on.

<p>
  <a href="https://archuser.org/fuelmath.apk">
    <img src="readme-assets/badge_obtainium.png" alt="Get it on Obtainium" width="188" height="56" valign="middle">
  </a>
  <a href="https://archuser.org/fuelmath.apk">
    <img src="readme-assets/badge-apk.png" alt="Download APK" width="145" height="56" valign="middle">
  </a>
</p>

Current version: `v0.3.0`

## Why Use It

Most maintenance records end up scattered across glove boxes, notebooks, receipts, text files, and memory. Fuel Math gives those records one simple place to live.

Use it to answer practical questions:

- What maintenance is overdue?
- What is coming due soon?
- Which service items still need a baseline record?
- How much have I spent on fuel, charging, and maintenance?
- What does this asset cost me per mile or per hour?

Fuel Math is not a fleet management platform. It is a local checkbook for keeping personally owned machines maintained.

## What It Tracks

- Multiple vehicles and equipment assets
- Preventative maintenance items and service history
- Mileage-based, hour-based, time-based, and mixed maintenance intervals
- Manual mileage and hour-meter updates
- Fuel purchases for gasoline, diesel, mixed gas, propane, natural gas, and hybrids
- Charging sessions for electric assets and plug-in hybrids
- Maintenance, fuel, charging, and operating cost summaries
- Local backup and restore using JSON files

## Built For Vehicles And Equipment

Fuel Math treats vehicles and equipment as first-class records. When you add an asset, you choose what it is and what powers it.

Examples:

- A gasoline truck can track mileage, fuel purchases, MPG, oil changes, tires, and brake work.
- A diesel skid loader can track hours, fuel use per hour, hydraulic service, filters, and grease points.
- An electric golf cart can track charging, battery-related maintenance, tires, brakes, and cost to run.
- A trailer can track inspections and service without forcing fuel or engine fields.

The app changes its fields and labels based on the asset. Electric assets do not get fuel logs. Hour-meter equipment does not have to pretend mileage is useful.

## Offline By Design

Fuel Math stores its records on your device. It does not require an account, internet access, cloud sync, or a subscription.

That also means you are responsible for keeping backups if the records matter to you. The app includes local JSON backup and restore so you can export your data and keep a copy somewhere safe.

## Current Limitations

Fuel Math is intentionally narrow. It does not include VIN decoding, OBD integration, manufacturer-specific service schedules, shop marketplaces, cloud sync, user accounts, payment tools, or commercial dispatch features.

The goal is simple: help you know what needs attention next and what it has cost to operate your equipment.

## For Builders

This repository contains a native Android app built with Kotlin, Android Views, and Material components.

Repository highlights:

- `app/`: Android application module
- `app/src/main/java/org/archuser/fuelmath/MainActivity.kt`: Main UI flow
- `app/src/main/java/org/archuser/fuelmath/FuelCalculator.kt`: Calculation and summary logic
- `app/src/main/java/org/archuser/fuelmath/Models.kt`: Core data models and enums
- `app/src/main/java/org/archuser/fuelmath/FuelRepository.kt`: Local persistence
- `app/src/main/java/org/archuser/fuelmath/FuelJsonCodec.kt`: JSON backup, restore, and migration logic
- `app/src/test/java/org/archuser/fuelmath/FuelCalculatorTest.kt`: Unit tests for calculation-heavy behavior

Build requirements:

- Android Studio or Android SDK command-line tools
- Java 11 or newer

Build a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Build a release APK:

```powershell
.\gradlew.bat assembleRelease
```

Release signing is used only when the expected keystore environment variables are configured. Without them, release builds remain unsigned.
