# Flight Tickets Price Tracker

Android app for searching sample flight fares, tracking target prices, and managing saved alerts. Version `1.0.0-beta` builds to a release APK at `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Mom priority trip defaults

The app now opens focused on the urgent family trip:

- Route: Casablanca Mohammed V (`CMN`) to Montréal-Trudeau (`YUL`).
- Dates: August 20, 2026 to February 10, 2027 by default, which is less than six months.
- Trip type: round trip.
- Filter: direct flights only.
- Airline preference: Air Canada is ranked first when its fare is not excessively higher because the route offer includes two checked bags; Royal Air Maroc is still shown and can win when it is considerably cheaper.
- Target alert: USD 850 by default.

The same screen can still search other routes by changing the origin, destination, dates, cabin, trip type, direct-only checkbox, passengers, and target price.

## Features

- Search flights by origin, destination, departure date, return date, passengers, cabin, direct-only preference, and trip type.
- Sort generated fare quotes by best practical value for the selected trip.
- Prioritize direct CMN-YUL family travel with a visa-safe return window under six months.
- Save a target-price alert for a route.
- Persist alerts locally on device with SharedPreferences.
- Offline demo fare engine for immediate use without account setup.

## Build and test

```bash
gradle test
ANDROID_HOME=$PWD/.android-sdk gradle assembleRelease
```

If a full Android SDK is unavailable, the core flight-prioritization logic can still be smoke-tested with `./scripts/smoke_test.sh`.
