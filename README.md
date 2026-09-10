# Live Flight Price Tracker

A native Android flight-price tracker using Google Flights results through SerpApi. The app never invents fares, airlines, or itineraries.

## Why SerpApi

The previous Amadeus integration required a two-part client ID/secret flow and production approval for real inventory. The tracker now uses SerpApi's Google Flights engine and supports a pool of up to five private API keys.

## Fastest setup: direct mode

This is intended for the owner's private Android device.

1. Create or obtain the SerpApi keys you are authorized to use.
2. Install/open Flight Tracker.
3. Tap **Provider** or **Configure**.
4. Choose **SerpApi on this device**.
5. Paste the primary key into **SERPAPI API KEY 1**.
6. Optionally add fallback keys in slots 2 through 5.
7. Tap **Save**.
8. Search a route. The default route is Montréal-Trudeau (YUL) to Casablanca (CMN).

The keys are encrypted using Android Keystore. The tracker starts each calendar month on Key 1. If SerpApi reports that the active key has exhausted its search quota or rejects the key, the app advances to the next configured key and remembers that active slot for the rest of the month. If every key is exhausted, it stops retrying the pool until the next calendar month resets it to Key 1.

Do not distribute an APK that contains or exposes personal provider keys.

## Secure backend mode

For a public/distributed app, deploy `server/` and configure `SERPAPI_API_KEY` plus optional `SERPAPI_API_KEY_2` through `SERPAPI_API_KEY_5`. A compact `SERPAPI_API_KEYS=key1,key2,...` form is also supported for local/server deployments. Then configure the Android app with only the backend HTTPS URL and optional backend access token.

The backend endpoint is:

`GET /api/v1/flights/search`

The backend rotates on SerpApi quota/rejected-key responses and does not generate fallback prices. Provider errors remain errors.

## Fare-monitor quota guard

The GitHub Actions fare monitor supports the same five numbered SerpApi secrets. By default its monthly guard is calculated as 23 logical searches per configured unique key, so one through five keys produce guards of 23, 46, 69, 92, or 115 searches. Set the repository variable `FLIGHT_MONITOR_MONTHLY_CALL_CAP` if the allowance attached to your keys changes.

The active monitor key slot and month are cached across workflow runs. API keys are never written to that state file.

For automated monitoring, configure repository secrets:

- `SERPAPI_API_KEY`
- `SERPAPI_API_KEY_2` through `SERPAPI_API_KEY_5` as needed
- `FLIGHT_WATCHLIST_JSON`

See `docs/FREE_FARE_MONITOR.md`.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Android SDK Build Tools 36.0.0
- Gradle 9.6.0
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
