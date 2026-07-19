# Architecture

## Product rule

The application may return provider data or an explicit error. It may never manufacture an airline, itinerary, or price.

## Android client

### Search flow

1. `MainActivity` validates route, dates, passengers, cabin, nonstop preference, and currency.
2. `FlightServiceFactory` selects one real data source:
   - `BackendFlightService`, recommended for production.
   - `AmadeusFlightService`, owner-only direct mode.
3. `AmadeusResponseParser` maps the official response into `FareQuote` objects.
4. Carrier names come from `dictionaries.carriers`. When a name is absent, the real IATA carrier code is shown rather than an invented name.
5. Results display provider environment and verification time.

### Credentials

`SecureConfigStore` encrypts provider configuration with AES-GCM using a key generated in Android Keystore. Provider secrets are never committed to the repository or built into the APK.

Direct mode is a private-device convenience. A public deployment must use the backend so provider secrets remain server-side.

### Tracking

- `AlertRepository` stores up to 25 search targets.
- `PriceHistoryRepository` stores the latest 30 provider observations per alert.
- `AlertCheckWorker` performs connected-network checks through WorkManager.
- `AlertScheduler` schedules unique periodic work every six hours.
- `NotificationHelper` posts a notification only when a provider price is at or below the saved target.

Android may delay periodic work because of battery optimization and Doze. Checks are reliable background work, not exact alarms.

## Backend

The FastAPI service in `server/`:

- obtains Amadeus OAuth client-credentials tokens;
- caches tokens until shortly before expiration;
- proxies Flight Offers Search;
- retries once after a provider 401;
- returns the unmodified provider payload inside a small metadata envelope;
- supports an optional `X-App-Token` gate;
- returns 503 when provider credentials are absent instead of fabricating data.

Environment variables:

- `AMADEUS_CLIENT_ID`
- `AMADEUS_CLIENT_SECRET`
- `AMADEUS_ENVIRONMENT=test|production`
- `FLIGHT_API_ACCESS_TOKEN`
- `HTTP_TIMEOUT_SECONDS`

## Provider limitations

Amadeus test data is limited and is not treated as live. Production keys are required for complete production access. Flight Offers Search coverage is determined by Amadeus and does not include every airline.
