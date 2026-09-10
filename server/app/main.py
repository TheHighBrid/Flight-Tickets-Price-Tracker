from __future__ import annotations

import asyncio
import hmac
import math
import os
import time
from dataclasses import dataclass
from datetime import date
from typing import Any, Mapping

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.responses import JSONResponse


MAX_SERPAPI_KEYS = 5


def _clean_api_keys(source: Mapping[str, str]) -> tuple[str, ...]:
    candidates: list[str] = []
    for index in range(1, MAX_SERPAPI_KEYS + 1):
        name = "SERPAPI_API_KEY" if index == 1 else f"SERPAPI_API_KEY_{index}"
        candidates.append(source.get(name, ""))

    # Optional compact form for local/server deployments. Individual numbered variables
    # take precedence in ordering, and duplicates are removed below.
    bulk = source.get("SERPAPI_API_KEYS", "")
    if bulk:
        candidates.extend(bulk.replace(",", "\n").splitlines())

    keys: list[str] = []
    for candidate in candidates:
        key = str(candidate or "").strip()
        if not key or key in keys:
            continue
        keys.append(key)
        if len(keys) == MAX_SERPAPI_KEYS:
            break
    return tuple(keys)


def _month_key(now_epoch: float | None = None) -> str:
    now = time.time() if now_epoch is None else now_epoch
    return time.strftime("%Y-%m", time.gmtime(now))


def _is_key_failure(status_code: int, detail: str | None) -> bool:
    if status_code in {401, 403, 429}:
        return True
    normalized = str(detail or "").lower()
    return any(
        marker in normalized
        for marker in (
            "run out of searches",
            "search limit",
            "quota",
            "api key was rejected",
            "invalid api key",
            "invalid key",
        )
    )


@dataclass(frozen=True)
class Settings:
    api_keys: tuple[str, ...]
    app_token: str
    timeout_seconds: float

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> "Settings":
        source = os.environ if env is None else env
        try:
            timeout_seconds = float(source.get("HTTP_TIMEOUT_SECONDS", "30"))
        except ValueError:
            timeout_seconds = 30.0
        if not math.isfinite(timeout_seconds):
            timeout_seconds = 30.0
        timeout_seconds = min(max(timeout_seconds, 1.0), 120.0)
        return cls(
            api_keys=_clean_api_keys(source),
            app_token=source.get("FLIGHT_API_ACCESS_TOKEN", "").strip(),
            timeout_seconds=timeout_seconds,
        )

    @property
    def api_key(self) -> str:
        """Compatibility alias for integrations that still inspect the primary key."""
        return self.api_keys[0] if self.api_keys else ""

    @property
    def configured(self) -> bool:
        return bool(self.api_keys)


class SerpApiProvider:
    ENDPOINT = "https://serpapi.com/search.json"

    def __init__(self, settings: Settings, *, initial_key_index: int = 0) -> None:
        self.settings = settings
        self._active_month = _month_key()
        self._active_key_index = min(max(int(initial_key_index), 0), len(settings.api_keys))
        self._rotation_lock = asyncio.Lock()

    @property
    def api_key_count(self) -> int:
        return len(self.settings.api_keys)

    @property
    def active_key_index(self) -> int:
        self._reset_if_new_month()
        return self._active_key_index

    @property
    def active_key_number(self) -> int | None:
        index = self.active_key_index
        return index + 1 if index < self.api_key_count else None

    def _reset_if_new_month(self) -> None:
        month = _month_key()
        if month != self._active_month:
            self._active_month = month
            self._active_key_index = 0

    async def _advance_key(self, failed_index: int) -> int:
        async with self._rotation_lock:
            self._reset_if_new_month()
            self._active_key_index = max(self._active_key_index, failed_index + 1)
            return self._active_key_index

    def _all_keys_exhausted(self) -> HTTPException:
        count = self.api_key_count
        noun = "key" if count == 1 else "keys"
        return HTTPException(
            status_code=429,
            detail=(
                f"All {count} configured SerpApi {noun} are out of quota or were rejected for this month. "
                "The pool resets to Key 1 automatically next calendar month."
            ),
        )

    async def search(self, params: dict[str, str]) -> dict[str, Any]:
        if not self.settings.configured:
            raise HTTPException(
                status_code=503,
                detail="No SerpApi API key is configured on the backend. Set SERPAPI_API_KEY or SERPAPI_API_KEYS.",
            )

        self._reset_if_new_month()
        key_index = self._active_key_index
        if key_index >= self.api_key_count:
            raise self._all_keys_exhausted()

        while key_index < self.api_key_count:
            request_params = dict(params)
            request_params["engine"] = "google_flights"
            request_params["api_key"] = self.settings.api_keys[key_index]
            async with httpx.AsyncClient(timeout=self.settings.timeout_seconds) as client:
                response = await client.get(self.ENDPOINT, params=request_params, headers={"Accept": "application/json"})

            if response.status_code >= 400:
                error = provider_error(response)
                if _is_key_failure(response.status_code, str(error.detail)):
                    key_index = await self._advance_key(key_index)
                    if key_index >= self.api_key_count:
                        raise self._all_keys_exhausted()
                    continue
                raise error

            payload = response_json(response, "flight search")
            provider_message = payload.get("error")
            if provider_message:
                if _is_key_failure(response.status_code, str(provider_message)):
                    key_index = await self._advance_key(key_index)
                    if key_index >= self.api_key_count:
                        raise self._all_keys_exhausted()
                    continue
                raise HTTPException(status_code=502, detail=str(provider_message))
            return payload

        raise self._all_keys_exhausted()


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
    version="2.2.0",
    description="Secure proxy for Google Flights results via SerpApi with automatic API-key rotation. No simulated fares are generated.",
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
        "configured_key_count": len(settings.api_keys),
        "active_key_number": provider.active_key_number,
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
        "provider_key_number": provider.active_key_number,
        "configured_key_count": provider.api_key_count,
        "fetched_at_epoch_ms": int(time.time() * 1000),
        "simulated_fares": False,
        "payload": payload,
    }
