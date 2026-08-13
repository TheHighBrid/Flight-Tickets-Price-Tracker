from app.fare_monitor import WatchRule, _empty_state, select_pairs


def test_flexible_watch_rotates_date_pairs():
    watch = WatchRule.from_dict(
        {
            "id": "watch-1",
            "origin": "YOW",
            "destination": "CMN",
            "departure_start": "2026-10-01",
            "departure_end": "2026-10-03",
            "trip_lengths_days": [7, 10],
            "target_price": 700,
            "samples_per_run": 2,
        },
        0,
    )
    state = _empty_state()
    first = select_pairs(watch, state)
    second = select_pairs(watch, state)

    assert [(x.departure_date, x.return_date) for x in first] == [
        ("2026-10-01", "2026-10-08"),
        ("2026-10-01", "2026-10-11"),
    ]
    assert [(x.departure_date, x.return_date) for x in second] == [
        ("2026-10-02", "2026-10-09"),
        ("2026-10-02", "2026-10-12"),
    ]
