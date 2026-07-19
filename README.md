# Live Flight Price Tracker

A native Android flight-price tracker that queries a real flight-offers provider. Version `2.0.0-beta` removes the offline simulator completely. The app does not generate prices, invent airline names, or fabricate itineraries.

## What changed in 2.0

- Real Amadeus Flight Offers Search integration.
- Real carrier names from the provider carrier dictionary.
- Actual flight numbers, segments, departure and arrival times, stops, durations, baggage metadata, currencies, and total prices.
- Secure Android Keystore storage for owner-entered provider credentials.
- Production-safe backend mode that keeps provider credentials off the phone.
- Saved target-price alerts checked through WorkManager about every six hours when Android and network conditions permit.
- Local provider-price history and notifications when a target is reached.
- CI fails if the removed invented airlines or deterministic fare generator reappear.

## No fake fallback

The app has no demo-price fallback. When configuration, authentication, networking, quota, or provider data fails, the app displays the real error. It never substitutes a generated fare.

## Configure the Android app

Open **Configure live provider** in the app and choose one mode.

### Secure backend mode, recommended

1. Deploy the service in [`server/`](server/README.md).
2. Add the backend HTTPS URL.
3. Add the backend access token if one was configured.

The provider API key and secret stay on the server.

### Amadeus API on this device, private use

1. Create an Amadeus for Developers application.
2. Enter the API key and secret in the app.
3. Choose **Production live inventory** only after Amadeus has issued a production key.

The app encrypts this configuration with Android Keystore. This mode is intended for the owner's private device, not public distribution. A public app should use the backend.

## Test versus production

The Amadeus test environment contains limited provider test data and is labeled as such in the app. It is not presented as live inventory. Production mode uses `https://api.amadeus.com` and requires a production key.

Amadeus Self-Service Flight Offers Search also has coverage limitations. Its documentation states that low-cost carriers, American Airlines, Delta, and British Airways are unavailable through this API. The app reports only what the provider returns.

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

```text
app/build/outputs/apk/debug/app-debug.apk
```

An optional repository variable named `FLIGHT_API_BASE_URL` can bake the default backend URL into CI builds. No provider secret or backend token is embedded.

## Architecture

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Security

Never commit Amadeus credentials, backend access tokens, signing keystores, or production secrets. See [`SECURITY.md`](SECURITY.md).
