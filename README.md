# Flight Tickets Price Tracker

Android app for searching sample flight fares, tracking target prices, and managing saved alerts. Version `1.0.0-beta` builds to a release APK at `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Features

- Search flights by origin, destination, passengers, cabin, and trip type.
- Sort generated fare quotes by best value.
- Save a target-price alert for a route.
- Persist alerts locally on device with SharedPreferences.
- Offline demo fare engine for immediate use without account setup.

## Build and test

```bash
gradle test
ANDROID_HOME=$PWD/.android-sdk gradle assembleRelease
```
