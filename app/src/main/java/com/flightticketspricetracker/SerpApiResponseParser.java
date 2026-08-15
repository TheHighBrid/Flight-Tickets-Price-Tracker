package com.flightticketspricetracker;

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
