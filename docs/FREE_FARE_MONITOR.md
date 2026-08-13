# Free Cloud Fare Monitor

This repository can monitor private flight targets from GitHub Actions without requiring a paid scheduler.

## What it does

- Runs every eight hours on a standard GitHub-hosted runner.
- Reads the watchlist from the encrypted `FLIGHT_WATCHLIST_JSON` GitHub Actions secret, so routes and travel dates do not need to be committed to the public repository.
- Uses real Amadeus production Flight Offers Search data only.
- Supports exact travel dates or flexible departure windows with rotating date-pair sampling.
- Filters by maximum stops, cabin, passenger count, currency, and target price.
- Builds a rolling 60-observation price history for each watch.
- Alerts on a hard target, a configurable historical drop, or a new record low.
- Suppresses duplicate alerts unless the fare materially improves or 72 hours have passed.
- Enforces both a monthly provider-call cap and a per-run call cap before making requests.
- Keeps route, date, watch-name, and itinerary details out of public workflow summaries.
- Creates a privacy-safe GitHub issue when a watch matches. The public issue contains only the opaque watch ID, price, currency, and trigger reason.

## Why the quota guard matters

Amadeus Self-Service provides a free monthly request quota in production, but the exact quota depends on the API and is shown in the Amadeus workspace. The monitor therefore defaults to a conservative hard cap of 100 Flight Offers Search calls per month.

Set the repository variable `FLIGHT_MONITOR_MONTHLY_CALL_CAP` to the free monthly Flight Offers Search quota shown in your Amadeus workspace. The monitor stops issuing provider searches when the cap is reached. Do not set this variable above your free quota if the goal is zero provider overage cost.

`FLIGHT_MONITOR_MAX_CALLS_PER_RUN` defaults to `4`.

## Required GitHub Actions secrets

### `AMADEUS_CLIENT_ID`

Production Amadeus Self-Service client ID.

### `AMADEUS_CLIENT_SECRET`

Production Amadeus Self-Service client secret.

### `FLIGHT_WATCHLIST_JSON`

A private JSON array. Always use an opaque `id` such as `watch-1` or `trip-a`. The ID can appear in a public GitHub alert issue, so do not use airport codes, city names, dates, names, or other private travel information in it.

Example flexible watch:

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
    "date_step_days": 1,
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

For fixed dates:

```json
[
  {
    "id": "watch-2",
    "name": "Private trip B",
    "origin": "YOW",
    "destination": "CDG",
    "departure_date": "2026-11-05",
    "return_date": "2026-11-16",
    "target_price": 650,
    "currency": "CAD",
    "max_stops": 1
  }
]
```

## Flexible-date scan behavior

A large travel window can represent many departure and return combinations. Searching every combination every eight hours would waste provider quota.

The monitor instead stores a cursor for each watch and rotates through candidate date pairs. `samples_per_run` controls how many pairs from that watch are checked per run. This makes broad monitoring predictable and quota-aware.

For example, a 20-day departure window with two trip lengths produces 40 date pairs. With two samples per run and three runs per day, a full sweep takes about 6.7 days.

For a high-priority trip, narrow the departure window or increase `samples_per_run` while keeping the monthly call cap within the free provider quota.

## Privacy model

The watchlist is stored as an encrypted GitHub Actions secret. Monitor state is stored in the Actions cache and contains price history under a SHA-256-derived opaque state key rather than the raw watch ID.

The public workflow summary intentionally prints counts only. When a deal matches, the workflow creates a public GitHub issue containing only the opaque watch ID, price, currency, and trigger reason. Route, travel dates, watch names, airlines, and itinerary details are omitted.

## Current limitation

Amadeus Self-Service Flight Offers Search does not cover every airline or every fare. Amadeus currently documents exclusions including American Airlines, Delta, British Airways, and low-cost carriers.

This means Amadeus should be treated as one provider, not the complete airfare market. The next architecture step is a provider interface with additional lawful real-fare data sources, so the tracker can compare independently sourced offers instead of depending on a single inventory feed.
