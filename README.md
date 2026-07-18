# Flight Tickets Price Tracker

A lightweight native Android application for exploring flight-search and price-alert workflows. Version `1.1.0-beta` supports trip dates, airport suggestions, multiple locally saved alerts, target evaluation, and an installable APK built by GitHub Actions.

> **Important:** the current fare engine is an offline simulator. It generates deterministic sample prices on the device and does not query airlines, travel agencies, or live inventory. Do not use demo prices to make purchasing decisions.

## What works

- Origin and destination entry with common-airport suggestions and strict IATA-code handling.
- One-way or round-trip searches with departure and return dates.
- Passenger count, cabin, and CAD or USD display selection.
- Sorted deterministic demo fares with total prices, stops, and duration.
- Multiple target-price alerts stored locally on the device.
- Manual alert checks against the current demo engine.
- Safe migration of the original single-alert storage format.
- Unit tests, Android lint, pull-request CI, and installable debug APK artifacts.

## Build and test

Requirements:

- JDK 17
- Android SDK 35
- Gradle 8.9

```bash
gradle --no-daemon test lint assembleDebug
```

The installable beta APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The release workflow intentionally publishes a debug-signed beta APK. A production release must use a protected signing key and a release signing configuration.

## Architecture

The app deliberately has no API secret embedded in the APK.

- `AirportCatalog` resolves supported city names and explicit IATA codes.
- `SearchCriteria` owns validation and normalized search inputs.
- `FlightSearchEngine` provides deterministic offline demo quotes.
- `PriceAlert` provides versioned alert serialization with legacy migration.
- `AlertRepository` manages up to 25 local alerts in `SharedPreferences`.
- `AlertEvaluator` compares saved targets with the best current demo quote.
- `MainActivity` renders the native Android interface.

More detail is available in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Connecting live fares safely

A live implementation should call a backend you control, not place an Amadeus, Sabre, Duffel, or airline API secret inside the Android app.

1. Create a server endpoint that stores provider secrets securely.
2. Validate and rate-limit incoming searches.
3. Exchange provider credentials server-side and normalize results.
4. Return only the fare data required by the app.
5. Add caching, request timeouts, retry limits, and provider error mapping.
6. Replace or wrap `FlightSearchEngine` with a network-backed provider while retaining demo mode as an explicit fallback.
7. Schedule background checks only after notification permission, battery behavior, and provider rate limits are handled.

## Current limitations

- No live inventory or booking links.
- No background notifications.
- The airport catalog is intentionally small; direct three-letter IATA codes are accepted.
- Alerts are local to one device and are not synchronized.
- Demo totals are generated for product testing, not market-price analysis.

## Security

Never commit API keys, provider secrets, signing keystores, passwords, or production endpoints containing credentials. See [`SECURITY.md`](SECURITY.md).
