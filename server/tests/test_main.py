from datetime import date, timedelta

import httpx
import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.main import Settings, app, response_json


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
            "origin": "YOW",
            "destination": "YOW",
            "departure_date": future_date(),
        },
    )
    assert response.status_code == 400
    assert "different" in response.json()["detail"].lower()


def test_search_requires_provider_configuration():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={
            "origin": "YOW",
            "destination": "CMN",
            "departure_date": future_date(),
        },
    )
    assert response.status_code == 503
    assert "serpapi_api_key" in response.json()["detail"].lower()


def test_search_rejects_invalid_calendar_date_before_provider_call():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={"origin": "YOW", "destination": "CMN", "departure_date": "2099-02-30"},
    )
    assert response.status_code == 422
    assert "valid calendar date" in response.json()["detail"]


def test_search_rejects_past_departure_before_provider_call():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={"origin": "YOW", "destination": "CMN", "departure_date": "2000-01-01"},
    )
    assert response.status_code == 400
    assert "past" in response.json()["detail"]


def test_search_rejects_return_before_departure():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={
            "origin": "YOW",
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


@pytest.mark.parametrize("content", [b"not-json", b"[]"])
def test_provider_json_rejects_malformed_or_non_object_payload(content):
    response = httpx.Response(200, content=content)
    with pytest.raises(HTTPException) as caught:
        response_json(response, "flight search")
    assert caught.value.status_code == 502
