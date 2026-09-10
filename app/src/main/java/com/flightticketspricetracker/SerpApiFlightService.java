package com.flightticketspricetracker;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

public final class SerpApiFlightService implements FlightService {
    private static final String ENDPOINT = "https://serpapi.com/search.json";
    private final ProviderConfig config;
    private final SecureConfigStore configStore;

    public SerpApiFlightService(ProviderConfig config) {
        this(config, null);
    }

    public SerpApiFlightService(ProviderConfig config, SecureConfigStore configStore) {
        this.config = config;
        this.configStore = configStore;
    }

    @Override
    public List<FareQuote> search(SearchCriteria criteria) throws FlightServiceException {
        String validation = criteria == null ? "Search criteria are required." : criteria.firstValidationError();
        if (validation != null) throw new FlightServiceException(validation, false);
        String configError = config.validationError();
        if (configError != null) throw new FlightServiceException(configError, false);

        int keyCount = config.apiKeyCount();
        int keyIndex = configStore == null ? 0 : configStore.currentSerpApiKeyIndex(config);
        if (keyIndex >= keyCount) throw exhaustedPool(keyCount);

        while (keyIndex < keyCount) {
            String apiKey = config.apiKeyAt(keyIndex);
            HttpTransport.Response response;
            try {
                response = HttpTransport.get(ENDPOINT + "?" + query(criteria, apiKey), null);
            } catch (IOException exception) {
                throw new FlightServiceException("Unable to reach SerpApi. Check the internet connection.", true, exception);
            }

            if (response.statusCode < 200 || response.statusCode >= 300) {
                String message = SerpApiResponseParser.errorMessage(response.body, response.statusCode);
                if (isKeyFailure(response.statusCode, message)) {
                    int next = rotateAfterFailure(keyIndex, keyCount);
                    if (next < 0) throw exhaustedPool(keyCount);
                    keyIndex = next;
                    continue;
                }
                throw new FlightServiceException(
                        message,
                        response.statusCode >= 500
                );
            }

            try {
                return SerpApiResponseParser.parse(
                        response.body,
                        criteria,
                        "direct/cache-enabled • key " + (keyIndex + 1) + "/" + keyCount
                );
            } catch (FlightServiceException exception) {
                if (isKeyFailure(response.statusCode, exception.getMessage())) {
                    int next = rotateAfterFailure(keyIndex, keyCount);
                    if (next < 0) throw exhaustedPool(keyCount);
                    keyIndex = next;
                    continue;
                }
                throw exception;
            }
        }

        throw exhaustedPool(keyCount);
    }

    private int rotateAfterFailure(int failedIndex, int keyCount) {
        if (configStore != null) return configStore.rotateSerpApiKey(config, failedIndex);
        int next = failedIndex + 1;
        return next < keyCount ? next : -1;
    }

    private static boolean isKeyFailure(int statusCode, String message) {
        if (statusCode == 401 || statusCode == 403 || statusCode == 429) return true;
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.US);
        return normalized.contains("run out of searches")
                || normalized.contains("search limit")
                || normalized.contains("quota")
                || normalized.contains("api key was rejected")
                || normalized.contains("invalid api key")
                || normalized.contains("invalid key");
    }

    private static FlightServiceException exhaustedPool(int keyCount) {
        String count = keyCount + " SerpApi key" + (keyCount == 1 ? "" : "s");
        return new FlightServiceException(
                "All " + count + " are out of quota or were rejected for this month. "
                        + "The key pool will automatically reset to Key 1 next calendar month.",
                false
        );
    }

    private String query(SearchCriteria criteria, String apiKey) {
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
        add(query, "api_key", apiKey);
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
