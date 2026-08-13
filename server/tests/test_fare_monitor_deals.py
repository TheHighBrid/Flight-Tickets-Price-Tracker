from app.fare_monitor import Quote, WatchRule, _empty_state, _state_key, cheapest_quote, evaluate_deal


def make_watch(**overrides):
    raw = {
        "id": "watch-1",
        "origin": "YOW",
        "destination": "CMN",
        "departure_date": "2026-10-01",
        "return_date": "2026-10-08",
        "target_price": 700,
        "max_stops": 1,
    }
    raw.update(overrides)
    return WatchRule.from_dict(raw, 0)


def test_cheapest_quote_respects_stop_limit():
    watch = make_watch()
    pair = watch.candidates()[0]
    payload = {
        "data": [
            {
                "id": "excluded",
                "price": {"grandTotal": "500", "currency": "CAD"},
                "itineraries": [{"segments": [{"carrierCode": "AC"}, {"carrierCode": "AT"}, {"carrierCode": "AT"}]}],
            },
            {
                "id": "accepted",
                "price": {"grandTotal": "650", "currency": "CAD"},
                "itineraries": [{"segments": [{"carrierCode": "AC"}, {"carrierCode": "AT"}]}],
            },
        ]
    }
    quote = cheapest_quote(payload, watch, pair)
    assert quote is not None
    assert quote.offer_id == "accepted"
    assert quote.price == 650
    assert quote.stops == 1


def test_target_alert_suppresses_duplicate_then_allows_better_fare():
    watch = make_watch()
    state = _empty_state()
    watch_state = state["watches"].setdefault(
        _state_key(watch.id),
        {"cursor": 0, "prices": [], "last_alert_price": None, "last_alert_at": 0},
    )
    first_quote = Quote(650, "CAD", 1, ("AC",), "1", "2026-10-01", "2026-10-08")
    first = evaluate_deal(watch, first_quote, watch_state, 1_000_000)
    duplicate = evaluate_deal(watch, first_quote, watch_state, 1_000_100)
    better = evaluate_deal(
        watch,
        Quote(620, "CAD", 1, ("AC",), "2", "2026-10-01", "2026-10-08"),
        watch_state,
        1_000_200,
    )
    assert first["alert"] is True
    assert duplicate["alert"] is False
    assert better["alert"] is True


def test_rolling_median_can_trigger_unusual_drop():
    watch = make_watch(target_price=None, drop_percent=15)
    state = _empty_state()
    watch_state = state["watches"].setdefault(
        _state_key(watch.id),
        {"cursor": 0, "prices": [1000.0, 980.0, 1020.0, 990.0], "last_alert_price": None, "last_alert_at": 0},
    )
    result = evaluate_deal(
        watch,
        Quote(800, "CAD", 0, ("AT",), "deal", "2026-10-01", "2026-10-08"),
        watch_state,
        1_000_000,
    )
    assert result["alert"] is True
    assert "historical_drop" in result["reasons"]
    assert result["baseline_price"] == 995.0
