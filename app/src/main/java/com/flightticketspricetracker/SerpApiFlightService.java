package com.flightticketspricetracker;

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
