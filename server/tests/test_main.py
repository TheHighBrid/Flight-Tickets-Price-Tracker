import asyncio
import json
from datetime import date, timedelta

import httpx
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.main import SerpApiProvider, Settings, app, response_json


def future_date(days: int = 30) -> str:
    return (date.today() + timedelta(days=days)).isoformat()


def test_health_never_claims_simulated_fares():
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    payload = response.json()
    assert payload["provider"] == "Google Flights via SerpApi"
    assert payload["simulated_fares"] is False


def test_search_rejects_same_airport_before_provider_call():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={
            "origin": "YUL",
            "destination": "YUL",
            "departure_date": future_date(),
        },
    )
    assert response.status_code == 400
    assert "different" in response.json()["detail"].lower()


def test_search_requires_provider_configuration():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={
            "origin": "YUL",
            "destination": "CMN",
            "departure_date": future_date(),
        },
    )
    assert response.status_code == 503
    assert "serpapi" in response.json()["detail"].lower()


def test_search_rejects_invalid_calendar_date_before_provider_call():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={"origin": "YUL", "destination": "CMN", "departure_date": "2099-02-30"},
    )
    assert response.status_code == 422
    assert "valid calendar date" in response.json()["detail"]


def test_search_rejects_past_departure_before_provider_call():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={"origin": "YUL", "destination": "CMN", "departure_date": "2000-01-01"},
    )
    assert response.status_code == 400
    assert "past" in response.json()["detail"]


def test_search_rejects_return_before_departure():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={
            "origin": "YUL",
            "destination": "CMN",
            "departure_date": future_date(30),
            "return_date": future_date(29),
        },
    )
    assert response.status_code == 400
    assert "before" in response.json()["detail"]


def test_settings_invalid_timeout_falls_back_and_values_are_bounded(monkeypatch):
    monkeypatch.setenv("HTTP_TIMEOUT_SECONDS", "not-a-number")
    assert Settings.from_env().timeout_seconds == 30.0
    monkeypatch.setenv("HTTP_TIMEOUT_SECONDS", "0")
    assert Settings.from_env().timeout_seconds == 1.0
    monkeypatch.setenv("HTTP_TIMEOUT_SECONDS", "999")
    assert Settings.from_env().timeout_seconds == 120.0
    monkeypatch.setenv("HTTP_TIMEOUT_SECONDS", "nan")
    assert Settings.from_env().timeout_seconds == 30.0


def test_settings_loads_up_to_five_unique_serpapi_keys_in_order():
    settings = Settings.from_env(
        {
            "SERPAPI_API_KEY": " key-1 ",
            "SERPAPI_API_KEY_2": "key-2",
            "SERPAPI_API_KEY_3": "key-1",
            "SERPAPI_API_KEYS": "key-3,key-4\nkey-5\nkey-6",
        }
    )
    assert settings.api_keys == ("key-1", "key-2", "key-3", "key-4", "key-5")
    assert settings.api_key == "key-1"
    assert settings.configured is True


def test_provider_rotates_to_second_key_after_quota_error(monkeypatch):
    calls: list[str] = []

    async def fake_get(self, url, *, params=None, headers=None):
        key = params["api_key"]
        calls.append(key)
        if key == "key-1":
            return httpx.Response(429, json={"error": "Your account has run out of searches."})
        return httpx.Response(200, json={"best_flights": []})

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)
    settings = Settings(api_keys=("key-1", "key-2"), app_token="", timeout_seconds=30.0)
    provider = SerpApiProvider(settings)

    result = asyncio.run(provider.search({"departure_id": "YUL", "arrival_id": "CMN"}))

    assert result == {"best_flights": []}
    assert calls == ["key-1", "key-2"]
    assert provider.active_key_number == 2


def test_provider_remembers_all_keys_exhausted_in_instance(monkeypatch):
    calls: list[str] = []

    async def fake_get(self, url, *, params=None, headers=None):
        calls.append(params["api_key"])
        return httpx.Response(429, json={"error": "Search limit reached"})

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)
    settings = Settings(api_keys=("key-1", "key-2"), app_token="", timeout_seconds=30.0)
    provider = SerpApiProvider(settings)

    with pytest.raises(HTTPException) as first:
        asyncio.run(provider.search({"departure_id": "YUL", "arrival_id": "CMN"}))
    assert first.value.status_code == 429
    assert provider.active_key_number is None
    assert calls == ["key-1", "key-2"]

    with pytest.raises(HTTPException):
        asyncio.run(provider.search({"departure_id": "YUL", "arrival_id": "CMN"}))
    assert calls == ["key-1", "key-2"]


def test_provider_persists_only_active_slot_and_month(monkeypatch, tmp_path):
    calls: list[str] = []
    state_path = tmp_path / "serpapi-key-state.json"

    async def fake_get(self, url, *, params=None, headers=None):
        key = params["api_key"]
        calls.append(key)
        if key == "key-1":
            return httpx.Response(429, json={"error": "Search limit reached"})
        return httpx.Response(200, json={"best_flights": []})

    monkeypatch.setattr(httpx.AsyncClient, "get", fake_get)
    settings = Settings(
        api_keys=("key-1", "key-2"),
        app_token="",
        timeout_seconds=30.0,
        key_state_path=str(state_path),
    )
    provider = SerpApiProvider(settings)
    asyncio.run(provider.search({"departure_id": "YUL", "arrival_id": "CMN"}))

    saved = json.loads(state_path.read_text(encoding="utf-8"))
    assert saved["index"] == 1
    raw_state = state_path.read_text(encoding="utf-8")
    assert "key-1" not in raw_state
    assert "key-2" not in raw_state

    restarted = SerpApiProvider(settings)
    assert restarted.active_key_number == 2
    assert calls == ["key-1", "key-2"]


@pytest.mark.parametrize("content", [b"not-json", b"[]"])
def test_provider_json_rejects_malformed_or_non_object_payload(content):
    response = httpx.Response(200, content=content)
    with pytest.raises(HTTPException) as caught:
        response_json(response, "flight search")
    assert caught.value.status_code == 502
