from __future__ import annotations

import hmac
import math
import os
import time
from dataclasses import dataclass
from datetime import date
from typing import Any

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.responses import JSONResponse


@dataclass(frozen=True)
class Settings:
    api_key: str
    app_token: str
    timeout_seconds: float

    @classmethod
    def from_env(cls) -> "Settings":
        try:
            timeout_seconds = float(os.getenv("HTTP_TIMEOUT_SECONDS", "30"))
        except ValueError:
            timeout_seconds = 30.0
        if not math.isfinite(timeout_seconds):
            timeout_seconds = 30.0
        timeout_seconds = min(max(timeout_seconds, 1.0), 120.0)
        return cls(
            api_key=os.getenv("SERPAPI_API_KEY", "").strip(),
            app_token=os.getenv("FLIGHT_API_ACCESS_TOKEN", "").strip(),
            timeout_seconds=timeout_seconds,
        )

    @property
    def configured(self) -> bool:
        return bool(self.api_key)


class SerpApiProvider:
    ENDPOINT = "https://serpapi.com/search.json"

    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    async def search(self, params: dict[str, str]) -> dict[str, Any]:
        if not self.settings.configured:
            raise HTTPException(status_code=503, detail="SERPAPI_API_KEY is not configured on the backend.")
        request_params = dict(params)
        request_params["engine"] = "google_flights"
        request_params["api_key"] = self.settings.api_key
        async with httpx.AsyncClient(timeout=self.settings.timeout_seconds) as client:
            response = await client.get(self.ENDPOINT, params=request_params, headers={"Accept": "application/json"})
        if response.status_code >= 400:
            raise provider_error(response)
        payload = response_json(response, "flight search")
        provider_message = payload.get("error")
        if provider_message:
            raise HTTPException(status_code=502, detail=str(provider_message))
        return payload


def provider_error(response: httpx.Response) -> HTTPException:
    detail = f"Flight provider returned HTTP {response.status_code}."
    try:
        payload = response.json()
        detail = payload.get("error") or payload.get("detail") or detail
    except Exception:
        pass
    status = response.status_code if response.status_code in {400, 401, 403, 404, 429} else 502
    return HTTPException(status_code=status, detail=detail)


def response_json(response: httpx.Response, operation: str) -> dict[str, Any]:
    try:
        payload = response.json()
    except ValueError as exception:
        raise HTTPException(status_code=502, detail=f"Provider {operation} response was not valid JSON.") from exception
    if not isinstance(payload, dict):
        raise HTTPException(status_code=502, detail=f"Provider {operation} response had an unexpected format.")
    return payload


settings = Settings.from_env()
provider = SerpApiProvider(settings)
app = FastAPI(
    title="Flight Tickets Price Tracker API",
    version="2.1.0",
    description="Secure proxy for Google Flights results via SerpApi. No simulated fares are generated.",
)


async def authorize(x_app_token: str | None = Header(default=None)) -> None:
    if settings.app_token and (x_app_token is None or not hmac.compare_digest(x_app_token, settings.app_token)):
        raise HTTPException(status_code=401, detail="Invalid backend access token.")


def parse_travel_date(value: str, field_name: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exception:
        raise HTTPException(status_code=422, detail=f"{field_name} must be a valid calendar date in YYYY-MM-DD format.") from exception


def travel_class_code(value: str) -> str:
    return {"ECONOMY": "1", "PREMIUM_ECONOMY": "2", "BUSINESS": "3", "FIRST": "4"}[value]


@app.exception_handler(httpx.RequestError)
async def handle_network_error(_, exception: httpx.RequestError) -> JSONResponse:
    return JSONResponse(status_code=502, content={"detail": f"Unable to reach flight provider: {exception.__class__.__name__}"})


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "provider": "Google Flights via SerpApi",
        "configured": settings.configured,
        "cache_enabled": True,
        "simulated_fares": False,
    }


@app.get("/api/v1/flights/search", dependencies=[Depends(authorize)])
async def search_flights(
    origin: str = Query(min_length=3, max_length=3, pattern="^[A-Za-z]{3}$"),
    destination: str = Query(min_length=3, max_length=3, pattern="^[A-Za-z]{3}$"),
    departure_date: str = Query(pattern=r"^\d{4}-\d{2}-\d{2}$"),
    return_date: str | None = Query(default=None, pattern=r"^\d{4}-\d{2}-\d{2}$"),
    adults: int = Query(default=1, ge=1, le=9),
    travel_class: str = Query(default="ECONOMY", pattern="^(ECONOMY|PREMIUM_ECONOMY|BUSINESS|FIRST)$"),
    non_stop: bool = Query(default=False),
    currency: str = Query(default="CAD", pattern="^[A-Z]{3}$"),
) -> dict[str, Any]:
    if origin.upper() == destination.upper():
        raise HTTPException(status_code=400, detail="Origin and destination must be different.")
    departure = parse_travel_date(departure_date, "departure_date")
    if departure < date.today():
        raise HTTPException(status_code=400, detail="departure_date cannot be in the past.")
    if return_date:
        returning = parse_travel_date(return_date, "return_date")
        if returning < departure:
            raise HTTPException(status_code=400, detail="return_date cannot be before departure_date.")

    params = {
        "departure_id": origin.upper(),
        "arrival_id": destination.upper(),
        "outbound_date": departure_date,
        "type": "1" if return_date else "2",
        "adults": str(adults),
        "travel_class": travel_class_code(travel_class),
        "currency": currency.upper(),
        "hl": "en",
        "gl": "ca",
        "sort_by": "2",
    }
    if return_date:
        params["return_date"] = return_date
    if non_stop:
        params["stops"] = "1"

    payload = await provider.search(params)
    return {
        "provider": "Google Flights via SerpApi",
        "environment": "cache-enabled",
        "fetched_at_epoch_ms": int(time.time() * 1000),
        "simulated_fares": False,
        "payload": payload,
    }
