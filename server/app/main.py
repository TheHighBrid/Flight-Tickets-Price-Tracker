from __future__ import annotations

import asyncio
import os
import time
from dataclasses import dataclass
from typing import Any

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.responses import JSONResponse


@dataclass(frozen=True)
class Settings:
    client_id: str
    client_secret: str
    environment: str
    app_token: str
    timeout_seconds: float

    @classmethod
    def from_env(cls) -> "Settings":
        environment = os.getenv("AMADEUS_ENVIRONMENT", "test").strip().lower()
        if environment not in {"test", "production"}:
            environment = "test"
        return cls(
            client_id=os.getenv("AMADEUS_CLIENT_ID", "").strip(),
            client_secret=os.getenv("AMADEUS_CLIENT_SECRET", "").strip(),
            environment=environment,
            app_token=os.getenv("FLIGHT_API_ACCESS_TOKEN", "").strip(),
            timeout_seconds=float(os.getenv("HTTP_TIMEOUT_SECONDS", "30")),
        )

    @property
    def configured(self) -> bool:
        return bool(self.client_id and self.client_secret)

    @property
    def base_url(self) -> str:
        return "https://api.amadeus.com" if self.environment == "production" else "https://test.api.amadeus.com"


class AmadeusProvider:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._token = ""
        self._token_expires_at = 0.0
        self._lock = asyncio.Lock()

    async def _access_token(self, force: bool = False) -> str:
        now = time.time()
        if not force and self._token and now < self._token_expires_at - 60:
            return self._token

        async with self._lock:
            now = time.time()
            if not force and self._token and now < self._token_expires_at - 60:
                return self._token
            if not self.settings.configured:
                raise HTTPException(
                    status_code=503,
                    detail="Live flight provider credentials are not configured on the backend.",
                )
            async with httpx.AsyncClient(timeout=self.settings.timeout_seconds) as client:
                response = await client.post(
                    f"{self.settings.base_url}/v1/security/oauth2/token",
                    headers={"Content-Type": "application/x-www-form-urlencoded"},
                    data={
                        "grant_type": "client_credentials",
                        "client_id": self.settings.client_id,
                        "client_secret": self.settings.client_secret,
                    },
                )
            if response.status_code >= 400:
                raise provider_error(response)
            payload = response.json()
            token = payload.get("access_token")
            if not token:
                raise HTTPException(status_code=502, detail="Provider authentication response did not include an access token.")
            self._token = token
            self._token_expires_at = now + int(payload.get("expires_in", 1800))
            return self._token

    async def search(self, params: dict[str, str]) -> dict[str, Any]:
        token = await self._access_token()
        response = await self._request_search(params, token)
        if response.status_code == 401:
            token = await self._access_token(force=True)
            response = await self._request_search(params, token)
        if response.status_code >= 400:
            raise provider_error(response)
        return response.json()

    async def _request_search(self, params: dict[str, str], token: str) -> httpx.Response:
        async with httpx.AsyncClient(timeout=self.settings.timeout_seconds) as client:
            return await client.get(
                f"{self.settings.base_url}/v2/shopping/flight-offers",
                headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
                params=params,
            )


def provider_error(response: httpx.Response) -> HTTPException:
    detail = f"Flight provider returned HTTP {response.status_code}."
    try:
        payload = response.json()
        errors = payload.get("errors") or []
        if errors:
            detail = errors[0].get("detail") or errors[0].get("title") or detail
        else:
            detail = payload.get("error_description") or payload.get("detail") or detail
    except Exception:
        pass
    status = response.status_code if response.status_code in {400, 401, 403, 404, 429} else 502
    return HTTPException(status_code=status, detail=detail)


settings = Settings.from_env()
provider = AmadeusProvider(settings)
app = FastAPI(
    title="Flight Tickets Price Tracker API",
    version="2.0.0-beta",
    description="Secure proxy for real Amadeus flight-offer inventory. No simulated fares are generated.",
)


async def authorize(x_app_token: str | None = Header(default=None)) -> None:
    if settings.app_token and x_app_token != settings.app_token:
        raise HTTPException(status_code=401, detail="Invalid backend access token.")


@app.exception_handler(httpx.RequestError)
async def handle_network_error(_, exception: httpx.RequestError) -> JSONResponse:
    return JSONResponse(status_code=502, content={"detail": f"Unable to reach flight provider: {exception.__class__.__name__}"})


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "provider": "Amadeus",
        "environment": settings.environment,
        "configured": settings.configured,
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
    max_offers: int = Query(default=20, ge=1, le=50),
) -> dict[str, Any]:
    if origin.upper() == destination.upper():
        raise HTTPException(status_code=400, detail="Origin and destination must be different.")
    params = {
        "originLocationCode": origin.upper(),
        "destinationLocationCode": destination.upper(),
        "departureDate": departure_date,
        "adults": str(adults),
        "travelClass": travel_class,
        "nonStop": str(non_stop).lower(),
        "currencyCode": currency.upper(),
        "max": str(max_offers),
    }
    if return_date:
        params["returnDate"] = return_date

    payload = await provider.search(params)
    return {
        "provider": "Amadeus",
        "environment": settings.environment,
        "fetched_at_epoch_ms": int(time.time() * 1000),
        "simulated_fares": False,
        "payload": payload,
    }
