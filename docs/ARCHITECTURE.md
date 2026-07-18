# Architecture

## Product boundary

The Android client is a demo-first flight search and alert application. It is complete enough to validate the local product flow, but it intentionally stops before live-provider integration because provider secrets cannot be safely shipped in an APK.

## Data flow

1. `MainActivity` collects user input.
2. `SearchCriteria` resolves airports and validates dates, route, passengers, cabin, and currency.
3. `FlightSearchEngine` generates deterministic sample quotes and sorts them by total price.
4. A selected search can be stored as a versioned `PriceAlert`.
5. `AlertRepository` persists alerts locally and migrates the original comma-separated alert format.
6. `AlertEvaluator` reruns saved searches and compares the best result with each target.

## Live-provider seam

For production, introduce a `FareProvider` abstraction with two implementations:

- `DemoFareProvider`, backed by the existing deterministic engine.
- `RemoteFareProvider`, backed by an HTTPS API owned by the project.

The remote API should perform provider authentication, input validation, normalization, caching, rate limiting, and observability. The Android client should receive short-lived, non-sensitive responses only.

## Background alerts

Background checking should be added only after live fares exist. A production implementation would use WorkManager with network constraints, server-controlled cadence, notification channels, and Android 13+ notification permission handling. Running periodic checks against the current simulator would create misleading notifications, so this version keeps alert evaluation manual and clearly labeled.
