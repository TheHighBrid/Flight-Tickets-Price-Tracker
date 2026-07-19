from fastapi.testclient import TestClient

from app.main import app


def test_health_never_claims_simulated_fares():
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    payload = response.json()
    assert payload["provider"] == "Amadeus"
    assert payload["simulated_fares"] is False


def test_search_rejects_same_airport_before_provider_call():
    response = TestClient(app).get(
        "/api/v1/flights/search",
        params={
            "origin": "YOW",
            "destination": "YOW",
            "departure_date": "2099-08-10",
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
            "departure_date": "2099-08-10",
        },
    )
    assert response.status_code == 503
    assert "credentials" in response.json()["detail"].lower()
