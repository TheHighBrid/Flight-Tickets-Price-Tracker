package com.flightticketspricetracker;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AmadeusFlightService implements FlightService {
    private final ProviderConfig config;
    private String accessToken = "";
    private long tokenExpiresAtMillis = 0L;

    public AmadeusFlightService(ProviderConfig config) {
        this.config = config;
    }

    @Override
    public List<FareQuote> search(SearchCriteria criteria) throws FlightServiceException {
        String validation = criteria == null ? "Search criteria are required." : criteria.firstValidationError();
        if (validation != null) throw new FlightServiceException(validation, false);
        String configError = config.validationError();
        if (configError != null) throw new FlightServiceException(configError, false);

        String token = token(false);
        HttpTransport.Response response = requestOffers(criteria, token);
        if (response.statusCode == 401) {
            token = token(true);
            response = requestOffers(criteria, token);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new FlightServiceException(
                    AmadeusResponseParser.errorMessage(response.body, response.statusCode),
                    response.statusCode == 429 || response.statusCode >= 500
            );
        }
        return AmadeusResponseParser.parse(response.body, criteria, config.environmentLabel());
    }

    private HttpTransport.Response requestOffers(SearchCriteria criteria, String token) throws FlightServiceException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        try {
            return HttpTransport.get(baseUrl() + "/v2/shopping/flight-offers?" + query(criteria), headers);
        } catch (IOException exception) {
            throw new FlightServiceException("Unable to reach Amadeus. Check the internet connection.", true, exception);
        }
    }

    private synchronized String token(boolean forceRefresh) throws FlightServiceException {
        long now = System.currentTimeMillis();
        if (!forceRefresh && !accessToken.isEmpty() && now < tokenExpiresAtMillis - 60_000L) return accessToken;

        String body = "grant_type=client_credentials&client_id=" + encode(config.apiKey)
                + "&client_secret=" + encode(config.apiSecret);
        HttpTransport.Response response;
        try {
            response = HttpTransport.post(
                    baseUrl() + "/v1/security/oauth2/token",
                    null,
                    "application/x-www-form-urlencoded",
                    body
            );
        } catch (IOException exception) {
            throw new FlightServiceException("Unable to authenticate with Amadeus.", true, exception);
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new FlightServiceException(
                    AmadeusResponseParser.errorMessage(response.body, response.statusCode),
                    response.statusCode >= 500
            );
        }
        try {
            JSONObject json = new JSONObject(response.body);
            accessToken = json.getString("access_token");
            int expiresIn = json.optInt("expires_in", 1800);
            tokenExpiresAtMillis = now + expiresIn * 1000L;
            return accessToken;
        } catch (JSONException exception) {
            throw new FlightServiceException("Amadeus authentication returned an unreadable response.", false, exception);
        }
    }

    private String query(SearchCriteria criteria) {
        StringBuilder query = new StringBuilder();
        add(query, "originLocationCode", criteria.origin);
        add(query, "destinationLocationCode", criteria.destination);
        add(query, "departureDate", criteria.departureDate);
        if (criteria.roundTrip) add(query, "returnDate", criteria.returnDate);
        add(query, "adults", Integer.toString(criteria.passengers));
        add(query, "travelClass", criteria.travelClassCode());
        add(query, "nonStop", Boolean.toString(criteria.nonStop));
        add(query, "currencyCode", criteria.currency);
        add(query, "max", "20");
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

    private String baseUrl() {
        return config.environment == ProviderConfig.Environment.PRODUCTION
                ? "https://api.amadeus.com"
                : "https://test.api.amadeus.com";
    }
}
