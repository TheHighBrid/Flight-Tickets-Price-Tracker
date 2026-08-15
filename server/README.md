# Live flight backend

This FastAPI service is the production-safe data path for the Android app. It keeps the SerpApi key on the server and proxies Google Flights results through SerpApi. It never manufactures fares or airline names.

## Run locally

```bash
cd server
python -m venv .venv
. .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env
# Export the values from .env in your shell.
uvicorn app.main:app --reload --port 8080
```

Required configuration:

- `SERPAPI_API_KEY`, your private SerpApi key
- `FLIGHT_API_ACCESS_TOKEN`, recommended when exposing the backend publicly

`HTTP_TIMEOUT_SECONDS` is optional and defaults to 30 seconds. Values are clamped to 1-120 seconds so a deployment typo cannot disable provider timeouts. Search requests reject invalid calendar dates, past departures, and returns before the departure before contacting the provider.

The Android app sends `FLIGHT_API_ACCESS_TOKEN` as `X-App-Token` when configured.

## Which mode should I use?

For an owner-only install on a private Android device, direct SerpApi mode is simpler and needs no backend deployment. The Android configuration store protects the provider key with Android Keystore.

For an APK that will be distributed to other people, use this backend instead of putting a shared provider key on end-user devices. Configure the app with the backend HTTPS URL and optional access token, while `SERPAPI_API_KEY` remains only in the server environment.

## Provider and quota behavior

The backend requests Google Flights results through SerpApi and returns provider errors as errors. It does not substitute synthetic data. SerpApi cache behavior remains enabled by default so identical searches may be served from the provider cache without forcing a fresh billable search.
