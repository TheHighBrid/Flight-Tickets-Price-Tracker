import asyncio

from app.fare_monitor import WatchRule, _empty_state, _state_key, run_monitor


def test_monthly_cap_blocks_extra_provider_calls_and_preserves_cursor():
    watch = WatchRule.from_dict(
        {
            "id": "watch-1",
            "origin": "YOW",
            "destination": "CMN",
            "departure_start": "2026-10-01",
            "departure_end": "2026-10-03",
            "trip_lengths_days": [7],
            "samples_per_run": 2,
        },
        0,
    )
    state = _empty_state()

    class Provider:
        def __init__(self):
            self.calls = 0

        async def search(self, params):
            self.calls += 1
            return {
                "data": [
                    {
                        "id": str(self.calls),
                        "price": {"grandTotal": "900", "currency": "CAD"},
                        "itineraries": [{"segments": [{"carrierCode": "AC"}]}],
                    }
                ]
            }

    provider = Provider()
    result = asyncio.run(
        run_monitor(
            provider,
            [watch],
            state,
            monthly_call_cap=1,
            max_calls_per_run=4,
            now_epoch=1_786_572_000,
        )
    )

    assert provider.calls == 1
    assert result["provider_calls_this_month"] == 1
    assert result["skipped_for_quota"] is True
    assert state["watches"][_state_key(watch.id)]["cursor"] == 1
