from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/flightticketspricetracker"


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_required(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


write(JAVA / "ProviderConfig.java", r'''package com.flightticketspricetracker;

import java.net.URLDecoder;
import java.net.URLEncoder;

public final class ProviderConfig {
    public enum Mode { SERPAPI_DIRECT, BACKEND }

    // Kept in the serialized model so older installs can be invalidated safely.
    // SerpApi itself does not have separate test/production hosts.
    public enum Environment { TEST, PRODUCTION }

    public final Mode mode;
    public final Environment environment;
    public final String apiKey;
    public final String apiSecret;
    public final String backendUrl;
    public final String backendToken;

    public ProviderConfig(
            Mode mode,
            Environment environment,
            String apiKey,
            String apiSecret,
            String backendUrl,
            String backendToken
    ) {
        this.mode = mode == null ? Mode.SERPAPI_DIRECT : mode;
        this.environment = environment == null ? Environment.PRODUCTION : environment;
        this.apiKey = clean(apiKey);
        this.apiSecret = ""; // SerpApi uses one API key only.
        this.backendUrl = trimSlash(clean(backendUrl));
        this.backendToken = clean(backendToken);
    }

    public static ProviderConfig empty(String defaultBackendUrl) {
        return new ProviderConfig(Mode.SERPAPI_DIRECT, Environment.PRODUCTION, "", "", defaultBackendUrl, "");
    }

    public boolean isConfigured() {
        if (mode == Mode.BACKEND) return backendUrl.startsWith("https://");
        return !apiKey.isEmpty();
    }

    public String validationError() {
        if (mode == Mode.BACKEND) {
            if (backendUrl.isEmpty()) return "Enter the HTTPS URL of the flight backend.";
            if (!backendUrl.startsWith("https://")) return "The backend URL must use HTTPS.";
            return null;
        }
        if (apiKey.isEmpty()) return "Enter your SerpApi API key.";
        return null;
    }

    public String environmentLabel() {
        return "cache-enabled";
    }

    public String statusLabel() {
        if (!isConfigured()) return "NOT CONFIGURED • Add a free SerpApi key";
        if (mode == Mode.BACKEND) return "SECURE BACKEND • " + backendUrl;
        return "GOOGLE FLIGHTS VIA SERPAPI • API key stored on this device";
    }

    public String encode() {
        return String.join("|", "v2", mode.name(), environment.name(),
                encoded(apiKey), encoded(""), encoded(backendUrl), encoded(backendToken));
    }

    public static ProviderConfig decode(String raw, String defaultBackendUrl) {
        if (raw == null || raw.trim().isEmpty()) return empty(defaultBackendUrl);
        String[] parts = raw.split("\\|", -1);
        // v1 contained Amadeus credentials. Do not silently reuse them with a different provider.
        if (parts.length != 7 || !"v2".equals(parts[0])) return empty(defaultBackendUrl);
        try {
            return new ProviderConfig(
                    Mode.valueOf(parts[1]), Environment.valueOf(parts[2]),
                    decoded(parts[3]), "", decoded(parts[5]), decoded(parts[6])
            );
        } catch (RuntimeException ignored) {
            return empty(defaultBackendUrl);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String encoded(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String decoded(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
''')

write(JAVA / "SerpApiFlightService.java", r'''package com.flightticketspricetracker;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

public final class SerpApiFlightService implements FlightService {
    private static final String ENDPOINT = "https://serpapi.com/search.json";
    private final ProviderConfig config;

    public SerpApiFlightService(ProviderConfig config) {
        this.config = config;
    }

    @Override
    public List<FareQuote> search(SearchCriteria criteria) throws FlightServiceException {
        String validation = criteria == null ? "Search criteria are required." : criteria.firstValidationError();
        if (validation != null) throw new FlightServiceException(validation, false);
        String configError = config.validationError();
        if (configError != null) throw new FlightServiceException(configError, false);

        HttpTransport.Response response;
        try {
            response = HttpTransport.get(ENDPOINT + "?" + query(criteria), null);
        } catch (IOException exception) {
            throw new FlightServiceException("Unable to reach SerpApi. Check the internet connection.", true, exception);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new FlightServiceException(
                    SerpApiResponseParser.errorMessage(response.body, response.statusCode),
                    response.statusCode == 429 || response.statusCode >= 500
            );
        }
        return SerpApiResponseParser.parse(response.body, criteria, "direct/cache-enabled");
    }

    private String query(SearchCriteria criteria) {
        StringBuilder query = new StringBuilder();
        add(query, "engine", "google_flights");
        add(query, "departure_id", criteria.origin);
        add(query, "arrival_id", criteria.destination);
        add(query, "outbound_date", criteria.departureDate);
        add(query, "type", criteria.roundTrip ? "1" : "2");
        if (criteria.roundTrip) add(query, "return_date", criteria.returnDate);
        add(query, "travel_class", travelClass(criteria.travelClassCode()));
        add(query, "adults", Integer.toString(criteria.passengers));
        if (criteria.nonStop) add(query, "stops", "1");
        add(query, "currency", criteria.currency);
        add(query, "hl", "en");
        add(query, "gl", "ca");
        add(query, "sort_by", "2");
        add(query, "api_key", config.apiKey);
        return query.toString();
    }

    private static String travelClass(String value) {
        if ("PREMIUM_ECONOMY".equals(value)) return "2";
        if ("BUSINESS".equals(value)) return "3";
        if ("FIRST".equals(value)) return "4";
        return "1";
    }

    private static void add(StringBuilder query, String key, String value) {
        if (query.length() > 0) query.append('&');
        query.append(encode(key)).append('=').append(encode(value));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
''')

write(JAVA / "SerpApiResponseParser.java", r'''package com.flightticketspricetracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SerpApiResponseParser {
    private SerpApiResponseParser() {}

    public static List<FareQuote> parse(String json, SearchCriteria criteria, String environment)
            throws FlightServiceException {
        try {
            JSONObject root = new JSONObject(json);
            String providerError = root.optString("error", "");
            if (!providerError.isEmpty()) throw new FlightServiceException(providerError, false);

            List<FareQuote> quotes = new ArrayList<>();
            appendOffers(root.optJSONArray("best_flights"), quotes, criteria, environment);
            appendOffers(root.optJSONArray("other_flights"), quotes, criteria, environment);
            Collections.sort(quotes);
            return quotes;
        } catch (JSONException | NumberFormatException exception) {
            throw new FlightServiceException("SerpApi returned an unreadable Google Flights response.", false, exception);
        }
    }

    public static String errorMessage(String json, int statusCode) {
        try {
            JSONObject root = new JSONObject(json);
            String error = root.optString("error", "");
            if (!error.isEmpty()) return error;
        } catch (JSONException ignored) {
            // Fall through to stable messages.
        }
        if (statusCode == 401 || statusCode == 403) return "The SerpApi API key was rejected.";
        if (statusCode == 429) return "The SerpApi search limit has been reached.";
        return "The flight provider returned HTTP " + statusCode + ".";
    }

    private static void appendOffers(
            JSONArray offers,
            List<FareQuote> quotes,
            SearchCriteria criteria,
            String environment
    ) throws JSONException {
        if (offers == null) return;
        long fetchedAt = System.currentTimeMillis();
        for (int index = 0; index < offers.length(); index++) {
            JSONObject offer = offers.optJSONObject(index);
            if (offer == null || !offer.has("price")) continue;
            JSONArray flights = offer.optJSONArray("flights");
            if (flights == null || flights.length() == 0) continue;

            Set<String> airlines = new LinkedHashSet<>();
            Set<String> flightNumbers = new LinkedHashSet<>();
            List<String> segments = new ArrayList<>();
            int duration = 0;
            for (int i = 0; i < flights.length(); i++) {
                JSONObject flight = flights.getJSONObject(i);
                String airline = flight.optString("airline", "").trim();
                if (!airline.isEmpty()) airlines.add(airline);
                String flightNumber = flight.optString("flight_number", "").trim();
                if (!flightNumber.isEmpty()) flightNumbers.add(flightNumber);
                duration += Math.max(0, flight.optInt("duration", 0));

                JSONObject departure = flight.optJSONObject("departure_airport");
                JSONObject arrival = flight.optJSONObject("arrival_airport");
                String depId = departure == null ? "?" : departure.optString("id", "?");
                String depTime = departure == null ? "time unavailable" : departure.optString("time", "time unavailable");
                String arrId = arrival == null ? "?" : arrival.optString("id", "?");
                String arrTime = arrival == null ? "time unavailable" : arrival.optString("time", "time unavailable");
                segments.add(depId + " " + depTime + " → " + arrId + " " + arrTime);
            }

            int totalDuration = offer.optInt("total_duration", duration);
            if (totalDuration <= 0) totalDuration = duration;
            String inbound = criteria.roundTrip
                    ? "Return flight details are chosen after selecting this outbound option."
                    : "";
            String token = offer.optString("departure_token", "");
            String offerId = token.isEmpty() ? "serpapi-" + (quotes.size() + 1) : "serpapi-" + Integer.toHexString(token.hashCode());

            quotes.add(new FareQuote(
                    offerId,
                    join(airlines, " + ", "Provider did not identify carrier"),
                    join(flightNumbers, " · ", "Flight number unavailable"),
                    criteria.route(),
                    join(segments, " | ", "Outbound itinerary unavailable"),
                    inbound,
                    Math.max(0, flights.length() - 1),
                    totalDuration,
                    new BigDecimal(String.valueOf(offer.get("price"))),
                    criteria.currency,
                    "Check the selected fare for baggage rules",
                    "Google Flights via SerpApi",
                    environment,
                    fetchedAt
            ));
        }
    }

    private static String join(Iterable<String> values, String separator, String fallback) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(separator);
            result.append(value.trim());
        }
        return result.length() == 0 ? fallback : result.toString();
    }
}
''')

write(JAVA / "FlightServiceFactory.java", r'''package com.flightticketspricetracker;

public final class FlightServiceFactory {
    private FlightServiceFactory() {}

    public static FlightService create(ProviderConfig config) {
        if (config.mode == ProviderConfig.Mode.BACKEND) return new BackendFlightService(config);
        return new SerpApiFlightService(config);
    }
}
''')

write(JAVA / "BackendFlightService.java", r'''package com.flightticketspricetracker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BackendFlightService implements FlightService {
    private final ProviderConfig config;

    public BackendFlightService(ProviderConfig config) {
        this.config = config;
    }

    @Override
    public List<FareQuote> search(SearchCriteria criteria) throws FlightServiceException {
        String validation = criteria == null ? "Search criteria are required." : criteria.firstValidationError();
        if (validation != null) throw new FlightServiceException(validation, false);
        String configError = config.validationError();
        if (configError != null) throw new FlightServiceException(configError, false);

        Map<String, String> headers = new HashMap<>();
        if (!config.backendToken.isEmpty()) headers.put("X-App-Token", config.backendToken);
        HttpTransport.Response response;
        try {
            response = HttpTransport.get(config.backendUrl + "/api/v1/flights/search?" + query(criteria), headers);
        } catch (IOException exception) {
            throw new FlightServiceException("Unable to reach the configured flight backend.", true, exception);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new FlightServiceException(
                    SerpApiResponseParser.errorMessage(response.body, response.statusCode),
                    response.statusCode == 429 || response.statusCode >= 500
            );
        }
        try {
            JSONObject envelope = new JSONObject(response.body);
            String environment = envelope.optString("environment", "cache-enabled");
            JSONObject payload = envelope.getJSONObject("payload");
            return SerpApiResponseParser.parse(payload.toString(), criteria, environment);
        } catch (JSONException exception) {
            throw new FlightServiceException("The flight backend returned an unreadable response.", false, exception);
        }
    }

    private static String query(SearchCriteria criteria) {
        StringBuilder query = new StringBuilder();
        add(query, "origin", criteria.origin);
        add(query, "destination", criteria.destination);
        add(query, "departure_date", criteria.departureDate);
        if (criteria.roundTrip) add(query, "return_date", criteria.returnDate);
        add(query, "adults", Integer.toString(criteria.passengers));
        add(query, "travel_class", criteria.travelClassCode());
        add(query, "non_stop", Boolean.toString(criteria.nonStop));
        add(query, "currency", criteria.currency);
        return query.toString();
    }

    private static void add(StringBuilder query, String key, String value) {
        if (query.length() > 0) query.append('&');
        query.append(encode(key)).append('=').append(encode(value));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
''')

# MainActivity is intentionally patched rather than rewritten so the rest of the UI stays untouched.
main = JAVA / "MainActivity.java"
replace_required(main, "ProviderConfig.Mode.AMADEUS_DIRECT", "ProviderConfig.Mode.SERPAPI_DIRECT")
replace_required(main, 'spinner(new String[]{"Secure backend", "Amadeus API on this device"})',
                 'spinner(new String[]{"SerpApi on this device", "Secure backend"})')
replace_required(main, 'mode.setSelection(current.mode == ProviderConfig.Mode.BACKEND ? 0 : 1);',
                 'mode.setSelection(current.mode == ProviderConfig.Mode.SERPAPI_DIRECT ? 0 : 1);')
replace_required(main,
'''        form.addView(fieldLabel("AMADEUS ENVIRONMENT"));
        Spinner environment = spinner(new String[]{"Production live inventory", "Test environment"});
        environment.setSelection(current.environment == ProviderConfig.Environment.PRODUCTION ? 0 : 1);
        form.addView(environment);

''', "")
replace_required(main, 'form.addView(fieldLabel("AMADEUS API KEY"));', 'form.addView(fieldLabel("SERPAPI API KEY"));')
replace_required(main,
'''        form.addView(fieldLabel("AMADEUS API SECRET"));
        EditText apiSecret = passwordInput(current.apiSecret);
        form.addView(apiSecret);

''', "")
replace_required(main,
'''                "Secure backend mode is recommended. Device mode stores credentials with Android Keystore. Test data stays clearly labeled and is never presented as live pricing.",''',
'''                "For private use, choose SerpApi on this device and paste one API key. The key is stored with Android Keystore. Secure backend mode remains available for distributed builds.",''')
replace_required(main,
'''                        mode.getSelectedItemPosition() == 0
                                ? ProviderConfig.Mode.BACKEND
                                : ProviderConfig.Mode.AMADEUS_DIRECT,
                        environment.getSelectedItemPosition() == 0
                                ? ProviderConfig.Environment.PRODUCTION
                                : ProviderConfig.Environment.TEST,
                        apiKey.getText().toString(),
                        apiSecret.getText().toString(),''',
'''                        mode.getSelectedItemPosition() == 0
                                ? ProviderConfig.Mode.SERPAPI_DIRECT
                                : ProviderConfig.Mode.BACKEND,
                        ProviderConfig.Environment.PRODUCTION,
                        apiKey.getText().toString(),
                        "",''')
replace_required(main,
'''        } else if (providerConfig.mode == ProviderConfig.Mode.SERPAPI_DIRECT
                && providerConfig.environment == ProviderConfig.Environment.TEST) {
            color = FlightTheme.WARNING;
        } else {''',
'''        } else {''')

# Old provider classes are removed so there is no accidental fallback to Amadeus.
for obsolete in ("AmadeusFlightService.java", "AmadeusResponseParser.java"):
    path = JAVA / obsolete
    if path.exists():
        path.unlink()

write(ROOT / "server/app/main.py", r'''from __future__ import annotations

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
''')

write(ROOT / "server/.env.example", '''SERPAPI_API_KEY=replace_me\nFLIGHT_API_ACCESS_TOKEN=choose_a_long_random_value\nHTTP_TIMEOUT_SECONDS=30''')

write(ROOT / "server/render.yaml", '''services:\n  - type: web\n    name: flight-tickets-price-tracker-api\n    runtime: docker\n    dockerfilePath: ./server/Dockerfile\n    dockerContext: ./server\n    healthCheckPath: /health\n    envVars:\n      - key: SERPAPI_API_KEY\n        sync: false\n      - key: FLIGHT_API_ACCESS_TOKEN\n        generateValue: true''')

# Patch monitor provider and response interpretation while preserving its alert/history logic.
monitor = ROOT / "server/app/fare_monitor.py"
replace_required(monitor, "from app.main import AmadeusProvider, Settings", "from app.main import SerpApiProvider, Settings")
replace_required(monitor,
'''def _offer_stops(offer: dict[str, Any]) -> int:
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
''',
'''def _offer_stops(offer: dict[str, Any]) -> int:
    flights = offer.get("flights") or []
    return max(len(flights) - 1, 0)


def _offer_carriers(offer: dict[str, Any]) -> tuple[str, ...]:
    seen: list[str] = []
    for segment in offer.get("flights") or []:
        name = str(segment.get("airline") or "").strip()
        if name and name not in seen:
            seen.append(name)
    return tuple(seen)


def _serpapi_offers(payload: dict[str, Any]) -> list[dict[str, Any]]:
    offers: list[dict[str, Any]] = []
    for key in ("best_flights", "other_flights"):
        value = payload.get(key) or []
        if isinstance(value, list):
            offers.extend(item for item in value if isinstance(item, dict))
    return offers
''')
replace_required(monitor,
'''    offers = payload.get("data") or []
    if not isinstance(offers, list):
        return None
    for offer in offers:
        if not isinstance(offer, dict):
            continue
        try:
            price_block = offer.get("price") or {}
            amount = float(price_block.get("grandTotal") or price_block.get("total"))
        except (TypeError, ValueError):
            continue
''',
'''    offers = _serpapi_offers(payload)
    for offer in offers:
        try:
            amount = float(offer.get("price"))
        except (TypeError, ValueError):
            continue
''')
replace_required(monitor,
'''        currency = str((offer.get("price") or {}).get("currency") or watch.currency).upper()
        if currency != watch.currency:
            continue
        candidates.append(
            Quote(
                price=amount,
                currency=currency,
                stops=stops,
                carriers=_offer_carriers(offer),
                offer_id=str(offer.get("id") or ""),
''',
'''        currency = watch.currency
        candidates.append(
            Quote(
                price=amount,
                currency=currency,
                stops=stops,
                carriers=_offer_carriers(offer),
                offer_id=str(offer.get("departure_token") or offer.get("booking_token") or ""),
''')
replace_required(monitor,
'''    params = {
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
''',
'''    travel_class = {"ECONOMY": "1", "PREMIUM_ECONOMY": "2", "BUSINESS": "3", "FIRST": "4"}[watch.travel_class]
    params = {
        "departure_id": watch.origin,
        "arrival_id": watch.destination,
        "outbound_date": pair.departure_date,
        "type": "1" if pair.return_date else "2",
        "adults": str(watch.adults),
        "travel_class": travel_class,
        "currency": watch.currency,
        "hl": "en",
        "gl": "ca",
        "sort_by": "2",
    }
    if watch.non_stop:
        params["stops"] = "1"
    if pair.return_date:
        params["return_date"] = pair.return_date
    return params
''')
replace_required(monitor, 'monthly_call_cap = int(os.getenv("FLIGHT_MONITOR_MONTHLY_CALL_CAP", "100"))',
                 'monthly_call_cap = int(os.getenv("FLIGHT_MONITOR_MONTHLY_CALL_CAP", "200"))')
replace_required(monitor, "provider = AmadeusProvider(Settings.from_env())", "provider = SerpApiProvider(Settings.from_env())")

# Backend tests: provider identity and configuration wording changed, validation behavior did not.
tests = ROOT / "server/tests/test_main.py"
replace_required(tests, 'assert payload["provider"] == "Amadeus"', 'assert payload["provider"] == "Google Flights via SerpApi"')
replace_required(tests, 'assert "credentials" in response.json()["detail"].lower()', 'assert "serpapi_api_key" in response.json()["detail"].lower()')

# Android unit test names and expected provider labels.
live_test = ROOT / "app/src/test/java/com/flightticketspricetracker/LiveModelTest.java"
replace_required(live_test, '"Amadeus", "production", 1000L', '"Google Flights via SerpApi", "cache-enabled", 1000L')
replace_required(live_test, 'assertTrue(quote.summary().contains("Amadeus production"));', 'assertTrue(quote.summary().contains("Google Flights via SerpApi cache-enabled"));')

# CI monitor secret migration.
workflow = ROOT / ".github/workflows/fare-monitor.yml"
replace_required(workflow,
'''          AMADEUS_CLIENT_ID: ${{ secrets.AMADEUS_CLIENT_ID }}
          AMADEUS_CLIENT_SECRET: ${{ secrets.AMADEUS_CLIENT_SECRET }}
          AMADEUS_ENVIRONMENT: production
          FLIGHT_WATCHLIST_JSON: ${{ secrets.FLIGHT_WATCHLIST_JSON }}
          FLIGHT_MONITOR_MONTHLY_CALL_CAP: ${{ vars.FLIGHT_MONITOR_MONTHLY_CALL_CAP || '100' }}''',
'''          SERPAPI_API_KEY: ${{ secrets.SERPAPI_API_KEY }}
          FLIGHT_WATCHLIST_JSON: ${{ secrets.FLIGHT_WATCHLIST_JSON }}
          FLIGHT_MONITOR_MONTHLY_CALL_CAP: ${{ vars.FLIGHT_MONITOR_MONTHLY_CALL_CAP || '200' }}''')

write(ROOT / "README.md", r'''# Live Flight Price Tracker

A native Android flight-price tracker using Google Flights results through SerpApi. The app never invents fares, airlines, or itineraries.

## Why SerpApi

The previous Amadeus integration required a two-part client ID/secret flow and production approval for real inventory. The tracker now uses one SerpApi API key. SerpApi has a free tier and its Google Flights engine returns structured flight-search results.

## Fastest setup: direct mode

This is intended for the owner's private Android device.

1. Create a SerpApi account.
2. Open your SerpApi account dashboard and copy your private API key.
3. Install/open Flight Tracker.
4. Tap **Provider** or **Configure**.
5. Choose **SerpApi on this device**.
6. Paste the key into **SERPAPI API KEY**.
7. Tap **Save**.
8. Search a route.

The key is encrypted using Android Keystore. Do not distribute an APK that contains or exposes a personal provider key.

## Secure backend mode

For a public/distributed app, deploy `server/` and put `SERPAPI_API_KEY` on the server. Then configure the Android app with only the backend HTTPS URL and optional backend access token.

The backend endpoint is:

`GET /api/v1/flights/search`

The backend does not generate fallback prices. Provider errors remain errors.

## Free-tier quota guard

The GitHub Actions fare monitor defaults to a hard cap of 200 provider searches per month so the app retains headroom under SerpApi's current free allowance. Exact identical searches may be served from SerpApi's one-hour cache and cached searches do not count toward the monthly search total.

For automated monitoring, configure repository secrets:

- `SERPAPI_API_KEY`
- `FLIGHT_WATCHLIST_JSON`

See `docs/FREE_FARE_MONITOR.md`.

## Build

Requirements:

- JDK 17
- Android SDK 35
- Android SDK Build Tools 36.0.0
- Gradle 9.5.0
- Python 3.13 for backend tests

```bash
cd server
pip install -r requirements-dev.txt
pytest -q
cd ..
bash scripts/smoke_test.sh
gradle --no-daemon --stacktrace test lint assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

## Security

Never commit SerpApi API keys, backend access tokens, signing keystores, or production secrets. See `SECURITY.md`.
''')

write(ROOT / "docs/FREE_FARE_MONITOR.md", r'''# Free Cloud Fare Monitor

This repository can monitor private flight targets from GitHub Actions without requiring a paid scheduler.

## Provider

The monitor uses Google Flights results through SerpApi. The free SerpApi plan currently includes 250 successful searches per month. Cached, errored, and failed searches do not count. SerpApi's Google Flights cache expires after one hour.

To leave room for manual searches in the Android app, this repository defaults to a hard cap of 200 provider searches per month. `FLIGHT_MONITOR_MAX_CALLS_PER_RUN` defaults to `4`.

## Required GitHub Actions secrets

### `SERPAPI_API_KEY`

Your private SerpApi API key.

### `FLIGHT_WATCHLIST_JSON`

A private JSON array. Use opaque IDs such as `watch-1` because an alert ID can appear in a public issue.

Example:

```json
[
  {
    "id": "watch-1",
    "name": "Private trip A",
    "origin": "YOW",
    "destination": "CMN",
    "departure_start": "2026-10-01",
    "departure_end": "2026-10-20",
    "trip_lengths_days": [14, 21],
    "samples_per_run": 2,
    "target_price": 750,
    "drop_percent": 15,
    "currency": "CAD",
    "max_stops": 1,
    "travel_class": "ECONOMY",
    "adults": 1
  }
]
```

## Flexible-date scan behavior

The monitor rotates through candidate date pairs instead of querying every possible pair on every run. This protects the free monthly allowance and keeps monitoring predictable.

## Privacy

The watchlist is stored as an encrypted GitHub Actions secret. Public workflow summaries omit routes, dates, watch names, and itineraries. Alert issues use opaque watch IDs.

## Important limitation

This is a fare-monitoring tool, not a ticketing engine. Google Flights results can change quickly and the final airline or agency checkout price must always be re-checked before purchase.
''')

write(ROOT / "SECURITY.md", r'''# Security

## Provider credentials

The current provider is Google Flights via SerpApi.

- Never commit `SERPAPI_API_KEY`.
- For a private owner-only Android install, direct mode stores the key using Android Keystore.
- For any distributed/public build, use secure backend mode and keep `SERPAPI_API_KEY` only in server environment variables.
- Never log request URLs that contain a provider API key.

## Backend

The optional backend accepts `FLIGHT_API_ACCESS_TOKEN` and sends it from the Android app as `X-App-Token`. Use a long random value and HTTPS only.

## GitHub Actions

Store `SERPAPI_API_KEY` and `FLIGHT_WATCHLIST_JSON` as encrypted repository secrets. Never place private routes, travel dates, or credentials in workflow source.
''')

# Keep historical release notes, but active architecture docs must reflect the current provider.
arch = ROOT / "docs/ARCHITECTURE.md"
if arch.exists():
    text = arch.read_text(encoding="utf-8")
    text = text.replace("AmadeusFlightService", "SerpApiFlightService")
    text = text.replace("AmadeusResponseParser", "SerpApiResponseParser")
    text = text.replace("Amadeus Flight Offers Search", "Google Flights via SerpApi")
    text = text.replace("Amadeus", "SerpApi")
    arch.write_text(text, encoding="utf-8")

# Basic migration invariants before committing.
main_text = main.read_text(encoding="utf-8")
for forbidden in ("AMADEUS API KEY", "AMADEUS API SECRET", "Amadeus API on this device", "Mode.AMADEUS_DIRECT"):
    if forbidden in main_text:
        raise RuntimeError(f"Migration left stale UI text: {forbidden}")
if "SERPAPI API KEY" not in main_text or "SerpApi on this device" not in main_text:
    raise RuntimeError("SerpApi provider UI was not installed")

print("SerpApi migration complete")
