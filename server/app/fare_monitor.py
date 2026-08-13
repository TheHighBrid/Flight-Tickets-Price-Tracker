from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import os
import statistics
import time
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Any, Protocol

from app.main import AmadeusProvider, Settings


def _iata(value: Any, field: str) -> str:
    code = str(value or "").strip().upper()
    if len(code) != 3 or not code.isalpha():
        raise ValueError(f"{field} must be a 3-letter IATA code.")
    return code


def _iso_date(value: Any, field: str) -> date:
    try:
        return date.fromisoformat(str(value))
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{field} must use YYYY-MM-DD.") from exc


def _positive_int(value: Any, field: str, default: int) -> int:
    if value is None:
        return default
    parsed = int(value)
    if parsed < 1:
        raise ValueError(f"{field} must be at least 1.")
    return parsed


@dataclass(frozen=True)
class SearchPair:
    departure_date: str
    return_date: str | None


@dataclass(frozen=True)
class WatchRule:
    id: str
    name: str
    origin: str
    destination: str
    fixed_departure: str | None
    fixed_return: str | None
    departure_start: str | None
    departure_end: str | None
    trip_lengths_days: tuple[int, ...]
    date_step_days: int
    adults: int
    travel_class: str
    non_stop: bool
    currency: str
    max_stops: int | None
    target_price: float | None
    drop_percent: float
    samples_per_run: int

    @classmethod
    def from_dict(cls, raw: dict[str, Any], index: int) -> "WatchRule":
        origin = _iata(raw.get("origin"), "origin")
        destination = _iata(raw.get("destination"), "destination")
        if origin == destination:
            raise ValueError("origin and destination must be different.")

        rule_id = str(raw.get("id") or f"{origin.lower()}-{destination.lower()}-{index + 1}").strip()
        if not rule_id:
            raise ValueError("id cannot be empty.")
        name = str(raw.get("name") or f"{origin} → {destination}").strip()

        fixed_departure = raw.get("departure_date")
        fixed_return = raw.get("return_date")
        departure_start = raw.get("departure_start")
        departure_end = raw.get("departure_end")

        if fixed_departure:
            dep = _iso_date(fixed_departure, "departure_date")
            fixed_departure = dep.isoformat()
            if fixed_return:
                ret = _iso_date(fixed_return, "return_date")
                if ret <= dep:
                    raise ValueError("return_date must be after departure_date.")
                fixed_return = ret.isoformat()
            departure_start = None
            departure_end = None
            trip_lengths = ()
        else:
            if not departure_start or not departure_end:
                raise ValueError(
                    "Each watch needs departure_date, or both departure_start and departure_end."
                )
            start = _iso_date(departure_start, "departure_start")
            end = _iso_date(departure_end, "departure_end")
            if end < start:
                raise ValueError("departure_end cannot be before departure_start.")
            departure_start = start.isoformat()
            departure_end = end.isoformat()
            fixed_return = None
            supplied_lengths = raw.get("trip_lengths_days", [])
            if supplied_lengths is None:
                supplied_lengths = []
            trip_lengths = tuple(sorted({_positive_int(v, "trip_lengths_days", 1) for v in supplied_lengths}))

        travel_class = str(raw.get("travel_class", "ECONOMY")).strip().upper()
        if travel_class not in {"ECONOMY", "PREMIUM_ECONOMY", "BUSINESS", "FIRST"}:
            raise ValueError("travel_class is invalid.")

        currency = str(raw.get("currency", "CAD")).strip().upper()
        if len(currency) != 3 or not currency.isalpha():
            raise ValueError("currency must be a 3-letter ISO currency code.")

        max_stops_raw = raw.get("max_stops")
        max_stops = None if max_stops_raw is None else int(max_stops_raw)
        if max_stops is not None and max_stops < 0:
            raise ValueError("max_stops cannot be negative.")

        target_raw = raw.get("target_price")
        target_price = None if target_raw is None else float(target_raw)
        if target_price is not None and target_price <= 0:
            raise ValueError("target_price must be greater than zero.")

        drop_percent = float(raw.get("drop_percent", 15.0))
        if not 0 <= drop_percent <= 90:
            raise ValueError("drop_percent must be between 0 and 90.")

        return cls(
            id=rule_id,
            name=name,
            origin=origin,
            destination=destination,
            fixed_departure=fixed_departure,
            fixed_return=fixed_return,
            departure_start=departure_start,
            departure_end=departure_end,
            trip_lengths_days=trip_lengths,
            date_step_days=_positive_int(raw.get("date_step_days"), "date_step_days", 1),
            adults=_positive_int(raw.get("adults"), "adults", 1),
            travel_class=travel_class,
            non_stop=bool(raw.get("non_stop", False)),
            currency=currency,
            max_stops=max_stops,
            target_price=target_price,
            drop_percent=drop_percent,
            samples_per_run=_positive_int(raw.get("samples_per_run"), "samples_per_run", 1),
        )

    def candidates(self) -> list[SearchPair]:
        if self.fixed_departure:
            return [SearchPair(self.fixed_departure, self.fixed_return)]

        assert self.departure_start and self.departure_end
        start = date.fromisoformat(self.departure_start)
        end = date.fromisoformat(self.departure_end)
        departure_dates: list[date] = []
        current = start
        while current <= end:
            departure_dates.append(current)
            current += timedelta(days=self.date_step_days)

        if not self.trip_lengths_days:
            return [SearchPair(dep.isoformat(), None) for dep in departure_dates]

        return [
            SearchPair(dep.isoformat(), (dep + timedelta(days=length)).isoformat())
            for dep in departure_dates
            for length in self.trip_lengths_days
        ]


class SearchProvider(Protocol):
    async def search(self, params: dict[str, str]) -> dict[str, Any]:
        ...


@dataclass(frozen=True)
class Quote:
    price: float
    currency: str
    stops: int
    carriers: tuple[str, ...]
    offer_id: str
    departure_date: str
    return_date: str | None


def _offer_stops(offer: dict[str, Any]) -> int:
    itineraries = offer.get("itineraries") or []
    if not itineraries:
        return 0
    return max(max(len(itinerary.get("segments") or []) - 1, 0) for itinerary in itineraries)


def _offer_carriers(offer: dict[str, Any]) -> tuple[str, ...]:
    seen: list[str] = []
    for itinerary in offer.get("itineraries") or []:
        for segment in itinerary.get("segments") or []:
            code = str(segment.get("carrierCode") or "").strip().upper()
            if code and code not in seen:
                seen.append(code)
    return tuple(seen)


def cheapest_quote(
    payload: dict[str, Any],
    watch: WatchRule,
    pair: SearchPair,
) -> Quote | None:
    candidates: list[Quote] = []
    for offer in payload.get("data") or []:
        try:
            price_block = offer.get("price") or {}
            amount = float(price_block.get("grandTotal") or price_block.get("total"))
        except (TypeError, ValueError):
            continue
        stops = _offer_stops(offer)
        if watch.max_stops is not None and stops > watch.max_stops:
            continue
        currency = str((offer.get("price") or {}).get("currency") or watch.currency).upper()
        candidates.append(
            Quote(
                price=amount,
                currency=currency,
                stops=stops,
                carriers=_offer_carriers(offer),
                offer_id=str(offer.get("id") or ""),
                departure_date=pair.departure_date,
                return_date=pair.return_date,
            )
        )
    return min(candidates, key=lambda quote: quote.price) if candidates else None


def provider_params(watch: WatchRule, pair: SearchPair) -> dict[str, str]:
    params = {
        "originLocationCode": watch.origin,
        "destinationLocationCode": watch.destination,
        "departureDate": pair.departure_date,
        "adults": str(watch.adults),
        "travelClass": watch.travel_class,
        "nonStop": str(watch.non_stop).lower(),
        "currencyCode": watch.currency,
        "max": "50",
    }
    if pair.return_date:
        params["returnDate"] = pair.return_date
    return params


def _empty_state() -> dict[str, Any]:
    return {"version": 1, "quota": {"month": "", "calls": 0}, "watches": {}}


def load_state(path: Path) -> dict[str, Any]:
    if not path.exists():
        return _empty_state()
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return _empty_state()
    if not isinstance(raw, dict):
        return _empty_state()
    raw.setdefault("version", 1)
    raw.setdefault("quota", {"month": "", "calls": 0})
    raw.setdefault("watches", {})
    return raw


def save_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(path)


def _month_key(now_epoch: float) -> str:
    return time.strftime("%Y-%m", time.gmtime(now_epoch))


def _ensure_month(state: dict[str, Any], now_epoch: float) -> None:
    month = _month_key(now_epoch)
    quota = state.setdefault("quota", {})
    if quota.get("month") != month:
        quota["month"] = month
        quota["calls"] = 0


def _state_key(watch_id: str) -> str:
    return hashlib.sha256(watch_id.encode("utf-8")).hexdigest()[:20]


def _watch_state(state: dict[str, Any], watch_id: str) -> dict[str, Any]:
    watches = state.setdefault("watches", {})
    return watches.setdefault(
        _state_key(watch_id),
        {
            "cursor": 0,
            "prices": [],
            "last_alert_price": None,
            "last_alert_at": 0,
        },
    )


def select_pairs(watch: WatchRule, state: dict[str, Any]) -> list[SearchPair]:
    candidates = watch.candidates()
    if not candidates:
        return []
    watch_state = _watch_state(state, watch.id)
    cursor = int(watch_state.get("cursor", 0)) % len(candidates)
    count = min(watch.samples_per_run, len(candidates))
    selected = [candidates[(cursor + offset) % len(candidates)] for offset in range(count)]
    watch_state["cursor"] = (cursor + count) % len(candidates)
    return selected


def _historical_baseline(prices: list[float]) -> float | None:
    if len(prices) < 4:
        return None
    return float(statistics.median(prices))


def evaluate_deal(
    watch: WatchRule,
    quote: Quote,
    watch_state: dict[str, Any],
    now_epoch: float,
) -> dict[str, Any]:
    prior_prices = [float(value) for value in watch_state.get("prices", []) if isinstance(value, (int, float))]
    baseline = _historical_baseline(prior_prices)
    previous_low = min(prior_prices) if prior_prices else None

    reasons: list[str] = []
    if watch.target_price is not None and quote.price <= watch.target_price:
        reasons.append("target_price")
    if baseline is not None and quote.price <= baseline * (1.0 - watch.drop_percent / 100.0):
        reasons.append("historical_drop")
    if previous_low is not None and quote.price <= previous_low * 0.98:
        reasons.append("new_record_low")

    prices = prior_prices + [quote.price]
    watch_state["prices"] = prices[-60:]

    last_alert_price = watch_state.get("last_alert_price")
    last_alert_at = float(watch_state.get("last_alert_at") or 0)
    improvement_required = max(15.0, float(last_alert_price or quote.price) * 0.02)
    materially_better = last_alert_price is None or quote.price <= float(last_alert_price) - improvement_required
    reminder_due = (now_epoch - last_alert_at) >= 72 * 3600
    should_alert = bool(reasons) and (materially_better or reminder_due)

    if should_alert:
        watch_state["last_alert_price"] = quote.price
        watch_state["last_alert_at"] = int(now_epoch)

    savings_percent = None
    if baseline and baseline > 0:
        savings_percent = round((baseline - quote.price) / baseline * 100.0, 1)

    return {
        "watch_id": watch.id,
        "watch_name": watch.name,
        "origin": watch.origin,
        "destination": watch.destination,
        "departure_date": quote.departure_date,
        "return_date": quote.return_date,
        "price": round(quote.price, 2),
        "currency": quote.currency,
        "stops": quote.stops,
        "carriers": list(quote.carriers),
        "offer_id": quote.offer_id,
        "baseline_price": round(baseline, 2) if baseline is not None else None,
        "savings_percent": savings_percent,
        "reasons": reasons,
        "alert": should_alert,
    }


def load_watchlist(env: dict[str, str] | None = None) -> list[WatchRule]:
    source = env if env is not None else os.environ
    raw_json = source.get("FLIGHT_WATCHLIST_JSON", "").strip()
    if raw_json:
        data = json.loads(raw_json)
    else:
        path_value = source.get("FLIGHT_WATCHLIST_PATH", "config/flight-watchlist.json")
        path = Path(path_value)
        if not path.exists():
            raise ValueError(
                "No watchlist configured. Set FLIGHT_WATCHLIST_JSON or provide FLIGHT_WATCHLIST_PATH."
            )
        data = json.loads(path.read_text(encoding="utf-8"))

    if not isinstance(data, list) or not data:
        raise ValueError("Flight watchlist must be a non-empty JSON array.")
    watches = [WatchRule.from_dict(item, index) for index, item in enumerate(data) if isinstance(item, dict)]
    if len(watches) != len(data):
        raise ValueError("Every watchlist entry must be a JSON object.")
    ids = [watch.id for watch in watches]
    if len(set(ids)) != len(ids):
        raise ValueError("Watch ids must be unique.")
    return watches


async def run_monitor(
    provider: SearchProvider,
    watches: list[WatchRule],
    state: dict[str, Any],
    *,
    monthly_call_cap: int,
    max_calls_per_run: int,
    now_epoch: float | None = None,
) -> dict[str, Any]:
    now = time.time() if now_epoch is None else now_epoch
    _ensure_month(state, now)
    quota = state["quota"]
    calls_this_run = 0
    observations: list[dict[str, Any]] = []
    errors: list[dict[str, str]] = []
    skipped_for_quota = False

    for watch in watches:
        pairs = select_pairs(watch, state)
        for pair_index, pair in enumerate(pairs):
            if calls_this_run >= max_calls_per_run or int(quota.get("calls", 0)) >= monthly_call_cap:
                skipped_for_quota = True
                remaining = len(pairs) - pair_index
                watch_state = _watch_state(state, watch.id)
                candidates = watch.candidates()
                if candidates:
                    watch_state["cursor"] = (int(watch_state.get("cursor", 0)) - remaining) % len(candidates)
                break
            quota["calls"] = int(quota.get("calls", 0)) + 1
            calls_this_run += 1
            try:
                payload = await provider.search(provider_params(watch, pair))
                quote = cheapest_quote(payload, watch, pair)
                if quote is None:
                    observations.append(
                        {
                            "watch_id": watch.id,
                            "watch_name": watch.name,
                            "departure_date": pair.departure_date,
                            "return_date": pair.return_date,
                            "price": None,
                            "currency": watch.currency,
                            "alert": False,
                            "reasons": [],
                            "status": "no_matching_offer",
                        }
                    )
                    continue
                observation = evaluate_deal(watch, quote, _watch_state(state, watch.id), now)
                observation["status"] = "ok"
                observations.append(observation)
            except Exception as exc:
                errors.append(
                    {
                        "watch_id": watch.id,
                        "departure_date": pair.departure_date,
                        "error": f"{exc.__class__.__name__}: {exc}",
                    }
                )
        if calls_this_run >= max_calls_per_run or int(quota.get("calls", 0)) >= monthly_call_cap:
            skipped_for_quota = True
            break

    alerts = [item for item in observations if item.get("alert")]
    return {
        "generated_at_epoch": int(now),
        "month": quota.get("month"),
        "provider_calls_this_run": calls_this_run,
        "provider_calls_this_month": int(quota.get("calls", 0)),
        "monthly_call_cap": monthly_call_cap,
        "skipped_for_quota": skipped_for_quota,
        "observation_count": len(observations),
        "alert_count": len(alerts),
        "alerts": alerts,
        "observations": observations,
        "errors": errors,
    }


def markdown_report(result: dict[str, Any]) -> str:
    alerts = result.get("alerts") or []
    lines = ["# ✈️ Flight fare alert", ""]
    for alert in alerts:
        route = f"{alert['origin']} → {alert['destination']}"
        dates = alert["departure_date"]
        if alert.get("return_date"):
            dates += f" → {alert['return_date']}"
        lines.extend(
            [
                f"## {alert['watch_name']}",
                "",
                f"- **Route:** {route}",
                f"- **Dates:** {dates}",
                f"- **Price:** {alert['price']:.2f} {alert['currency']}",
                f"- **Stops:** {alert['stops']}",
                f"- **Carriers:** {', '.join(alert.get('carriers') or ['provider did not return a code'])}",
                f"- **Trigger:** {', '.join(alert.get('reasons') or [])}",
            ]
        )
        if alert.get("baseline_price") is not None:
            lines.append(f"- **Rolling median:** {alert['baseline_price']:.2f} {alert['currency']}")
        if alert.get("savings_percent") is not None:
            lines.append(f"- **Vs. rolling median:** {alert['savings_percent']:.1f}% lower")
        lines.append("")
    lines.extend(
        [
            "---",
            f"Provider calls this month: {result.get('provider_calls_this_month', 0)} / {result.get('monthly_call_cap', 0)}",
            "",
            "This alert reports provider inventory only. Re-check the fare before purchasing because airline prices can change quickly.",
        ]
    )
    return "\n".join(lines).rstrip() + "\n"


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Quota-capped cloud fare monitor.")
    parser.add_argument("--state", default=".fare-monitor/state.json")
    parser.add_argument("--output", default=".fare-monitor/result.json")
    parser.add_argument("--markdown", default=".fare-monitor/alert.md")
    return parser


async def _main_async(args: argparse.Namespace) -> int:
    watches = load_watchlist()
    state_path = Path(args.state)
    state = load_state(state_path)

    monthly_call_cap = int(os.getenv("FLIGHT_MONITOR_MONTHLY_CALL_CAP", "100"))
    max_calls_per_run = int(os.getenv("FLIGHT_MONITOR_MAX_CALLS_PER_RUN", "4"))
    if monthly_call_cap < 1 or max_calls_per_run < 1:
        raise ValueError("Monitor call caps must be positive integers.")

    provider = AmadeusProvider(Settings.from_env())
    result = await run_monitor(
        provider,
        watches,
        state,
        monthly_call_cap=monthly_call_cap,
        max_calls_per_run=max_calls_per_run,
    )

    save_state(state_path, state)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    markdown_path = Path(args.markdown)
    markdown_path.parent.mkdir(parents=True, exist_ok=True)
    markdown_path.write_text(markdown_report(result), encoding="utf-8")

    print(json.dumps({
        "provider_calls_this_run": result["provider_calls_this_run"],
        "provider_calls_this_month": result["provider_calls_this_month"],
        "monthly_call_cap": result["monthly_call_cap"],
        "observations": result["observation_count"],
        "alerts": result["alert_count"],
        "errors": len(result["errors"]),
        "skipped_for_quota": result["skipped_for_quota"],
    }, sort_keys=True))
    return 0 if not result["errors"] else 2


def main() -> int:
    return asyncio.run(_main_async(_parser().parse_args()))


if __name__ == "__main__":
    raise SystemExit(main())
