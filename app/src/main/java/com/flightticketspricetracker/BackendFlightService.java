package com.flightticketspricetracker;

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
                    AmadeusResponseParser.errorMessage(response.body, response.statusCode),
                    response.statusCode == 429 || response.statusCode >= 500
            );
        }
        try {
            JSONObject envelope = new JSONObject(response.body);
            String environment = envelope.optString("environment", "production");
            JSONObject payload = envelope.getJSONObject("payload");
            return AmadeusResponseParser.parse(payload.toString(), criteria, environment);
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
