# Free Cloud Fare Monitor

This repository can monitor private flight targets from GitHub Actions without requiring a paid scheduler.

## Provider and key rotation

The monitor uses Google Flights results through SerpApi and accepts up to five private API keys. Configure the primary key as `SERPAPI_API_KEY` and optional fallbacks as `SERPAPI_API_KEY_2` through `SERPAPI_API_KEY_5`.

The monitor starts with Key 1. When SerpApi reports quota exhaustion or rejects the active key, the provider advances to the next configured key. The active slot and calendar month are stored in `.fare-monitor/serpapi-key-state.json`, which is retained by the private workflow cache. The state contains only the month and slot number, never an API key.

At the start of a new calendar month, the pool resets to Key 1 automatically.

The workflow's default quota guard is 23 logical searches per configured unique key. That means the default monthly guard is 23 with one key, 46 with two, 69 with three, 92 with four, and 115 with five. Override this with the repository variable `FLIGHT_MONITOR_MONTHLY_CALL_CAP` if the allowance attached to your SerpApi keys changes. `FLIGHT_MONITOR_MAX_CALLS_PER_RUN` defaults to `4`.

## Required GitHub Actions secrets

### `SERPAPI_API_KEY`

Primary private SerpApi API key.

### `SERPAPI_API_KEY_2` through `SERPAPI_API_KEY_5`

Optional fallback keys. Blank slots are ignored and duplicate values are removed from the pool.

### `FLIGHT_WATCHLIST_JSON`

A private JSON array. Use opaque IDs such as `watch-1` because an alert ID can appear in a public issue.

Example:

```json
[
  {
    "id": "watch-1",
    "name": "Private trip A",
    "origin": "YUL",
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

The monitor rotates through candidate date pairs instead of querying every possible pair on every run. This protects the monthly allowance and keeps monitoring predictable.

## Privacy

The watchlist is stored as an encrypted GitHub Actions secret. Public workflow summaries omit routes, dates, watch names, and itineraries. Alert issues use opaque watch IDs. Rotation state contains no credentials.

## Important limitation

This is a fare-monitoring tool, not a ticketing engine. Google Flights results can change quickly and the final airline or agency checkout price must always be re-checked before purchase.
