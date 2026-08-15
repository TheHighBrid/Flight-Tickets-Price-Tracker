# Live Flight Price Tracker

A native Android flight-price tracker using Google Flights results through SerpApi. The app never invents fares, airlines, or itineraries.

## Why SerpApi

The previous Amadeus integration required a two-part client ID/secret flow and production approval for real inventory. The tracker now uses one SerpApi API key. SerpApi has a free tier and its Google Flights engine returns structured flight-search results.

## Fastest setup: direct mode

This is intended for the owner's private Android device.

1. Create a SerpApi account.
2. Open your SerpApi account dashboard and copy your private API key.
3. Install/open Flight Tracker.
4. Tap **Provider** or **Configure**.
5. Choose **SerpApi on this device**.
6. Paste the key into **SERPAPI API KEY**.
7. Tap **Save**.
8. Search a route.

The key is encrypted using Android Keystore. Do not distribute an APK that contains or exposes a personal provider key.

## Secure backend mode

For a public/distributed app, deploy `server/` and put `SERPAPI_API_KEY` on the server. Then configure the Android app with only the backend HTTPS URL and optional backend access token.

The backend endpoint is:

`GET /api/v1/flights/search`

The backend does not generate fallback prices. Provider errors remain errors.

## Free-tier quota guard

The GitHub Actions fare monitor defaults to a hard cap of 200 provider searches per month so the app retains headroom under SerpApi's current free allowance. Exact identical searches may be served from SerpApi's one-hour cache and cached searches do not count toward the monthly search total.

For automated monitoring, configure repository secrets:

- `SERPAPI_API_KEY`
- `FLIGHT_WATCHLIST_JSON`

See `docs/FREE_FARE_MONITOR.md`.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Android SDK Build Tools 36.0.0
- Gradle 9.5.0
- Python 3.13 for backend tests

```bash
cd server
pip install -r requirements-dev.txt
pytest -q
cd ..
bash scripts/smoke_test.sh
gradle --no-daemon --stacktrace test lint assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Security

Never commit SerpApi API keys, backend access tokens, signing keystores, or production secrets. See `SECURITY.md`.
