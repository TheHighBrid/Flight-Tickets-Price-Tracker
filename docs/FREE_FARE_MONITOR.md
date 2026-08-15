# Free Cloud Fare Monitor

This repository can monitor private flight targets from GitHub Actions without requiring a paid scheduler.

## Provider

The monitor uses Google Flights results through SerpApi. The free SerpApi plan currently includes 250 successful searches per month. Cached, errored, and failed searches do not count. SerpApi's Google Flights cache expires after one hour.

To leave room for manual searches in the Android app, this repository defaults to a hard cap of 200 provider searches per month. `FLIGHT_MONITOR_MAX_CALLS_PER_RUN` defaults to `4`.

## Required GitHub Actions secrets

### `SERPAPI_API_KEY`

Your private SerpApi API key.

### `FLIGHT_WATCHLIST_JSON`

A private JSON array. Use opaque IDs such as `watch-1` because an alert ID can appear in a public issue.

Example:

```json
[
  {
    "id": "watch-1",
    "name": "Private trip A",
    "origin": "YOW",
    "destination": "CMN",
    "departure_start": "2026-10-01",
    "departure_end": "2026-10-20",
    "trip_lengths_days": [14, 21],
    "samples_per_run": 2,
    "target_price": 750,
    "drop_percent": 15,
    "currency": "CAD",
    "max_stops": 1,
    "travel_class": "ECONOMY",
    "adults": 1
  }
]
```

## Flexible-date scan behavior

The monitor rotates through candidate date pairs instead of querying every possible pair on every run. This protects the free monthly allowance and keeps monitoring predictable.

## Privacy

The watchlist is stored as an encrypted GitHub Actions secret. Public workflow summaries omit routes, dates, watch names, and itineraries. Alert issues use opaque watch IDs.

## Important limitation

This is a fare-monitoring tool, not a ticketing engine. Google Flights results can change quickly and the final airline or agency checkout price must always be re-checked before purchase.
