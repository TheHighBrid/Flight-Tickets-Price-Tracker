# Security

## Provider credentials

The current provider is Google Flights via SerpApi.

- Never commit `SERPAPI_API_KEY`.
- For a private owner-only Android install, direct mode stores the key using Android Keystore.
- For any distributed/public build, use secure backend mode and keep `SERPAPI_API_KEY` only in server environment variables.
- Never log request URLs that contain a provider API key.

## Backend

The optional backend accepts `FLIGHT_API_ACCESS_TOKEN` and sends it from the Android app as `X-App-Token`. Use a long random value and HTTPS only.

## GitHub Actions

Store `SERPAPI_API_KEY` and `FLIGHT_WATCHLIST_JSON` as encrypted repository secrets. Never place private routes, travel dates, or credentials in workflow source.
