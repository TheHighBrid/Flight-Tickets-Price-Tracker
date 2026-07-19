# Changelog

## 2.0.0-beta

- Removed every deterministic fare calculation and invented airline name.
- Added real Amadeus OAuth client-credentials authentication and Flight Offers Search.
- Added actual carrier names, flight numbers, itinerary segments, times, stops, durations, baggage, currencies, and provider timestamps.
- Added secure Android Keystore configuration storage.
- Added a production-safe FastAPI proxy backend with optional application-token protection.
- Added real saved-fare checks, provider-price history, native Android JobScheduler checks, and target notifications.
- Added explicit test-versus-production labeling.
- Added CI enforcement that rejects the former fake carrier names and simulator logic.
- Added backend tests, Android tests, lint, APK compilation, and release checks.

## 1.1.1-beta

- Repaired Android build workflows and compiled the offline prototype.
- This version is superseded because its fares and airlines were simulated.
