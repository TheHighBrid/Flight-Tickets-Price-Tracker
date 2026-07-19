package com.flightticketspricetracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AmadeusResponseParser {
    private AmadeusResponseParser() {}

    public static List<FareQuote> parse(String json, SearchCriteria criteria, String environment)
            throws FlightServiceException {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray data = root.optJSONArray("data");
            if (data == null) return Collections.emptyList();
            JSONObject dictionaries = root.optJSONObject("dictionaries");
            JSONObject carriers = dictionaries == null ? new JSONObject() : dictionaries.optJSONObject("carriers");
            if (carriers == null) carriers = new JSONObject();

            List<FareQuote> quotes = new ArrayList<>();
            long fetchedAt = System.currentTimeMillis();
            for (int index = 0; index < data.length(); index++) {
                JSONObject offer = data.getJSONObject(index);
                JSONObject price = offer.getJSONObject("price");
                JSONArray itineraries = offer.getJSONArray("itineraries");

                Itinerary outbound = parseItinerary(itineraries.getJSONObject(0), carriers);
                Itinerary inbound = itineraries.length() > 1
                        ? parseItinerary(itineraries.getJSONObject(1), carriers)
                        : Itinerary.empty();

                Set<String> airlineNames = new LinkedHashSet<>();
                airlineNames.addAll(outbound.airlineNames);
                airlineNames.addAll(inbound.airlineNames);
                Set<String> flightNumbers = new LinkedHashSet<>();
                flightNumbers.addAll(outbound.flightNumbers);
                flightNumbers.addAll(inbound.flightNumbers);

                quotes.add(new FareQuote(
                        offer.optString("id", Integer.toString(index + 1)),
                        join(airlineNames, " + "),
                        join(flightNumbers, " · "),
                        criteria.route(),
                        outbound.summary,
                        inbound.summary,
                        outbound.stops + inbound.stops,
                        outbound.durationMinutes + inbound.durationMinutes,
                        new BigDecimal(price.getString("total")),
                        price.optString("currency", criteria.currency),
                        baggageLabel(offer),
                        "Amadeus",
                        environment,
                        fetchedAt
                ));
            }
            Collections.sort(quotes);
            return quotes;
        } catch (JSONException | NumberFormatException exception) {
            throw new FlightServiceException("The flight provider returned an unreadable response.", false, exception);
        }
    }

    public static String errorMessage(String json, int statusCode) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray errors = root.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject error = errors.getJSONObject(0);
                String detail = error.optString("detail");
                String title = error.optString("title");
                if (!detail.isEmpty()) return detail;
                if (!title.isEmpty()) return title;
            }
            String description = root.optString("error_description");
            if (!description.isEmpty()) return description;
            String detail = root.optString("detail");
            if (!detail.isEmpty()) return detail;
        } catch (JSONException ignored) {
            // Fall through to a stable message.
        }
        if (statusCode == 401) return "The provider credentials were rejected.";
        if (statusCode == 429) return "The provider request limit has been reached.";
        return "The flight provider returned HTTP " + statusCode + ".";
    }

    private static Itinerary parseItinerary(JSONObject itinerary, JSONObject carriers) throws JSONException {
        JSONArray segments = itinerary.getJSONArray("segments");
        Set<String> airlineNames = new LinkedHashSet<>();
        Set<String> flightNumbers = new LinkedHashSet<>();
        List<String> segmentText = new ArrayList<>();

        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.getJSONObject(i);
            String carrierCode = segment.optString("carrierCode", "");
            String carrierName = carriers.optString(carrierCode, carrierCode);
            if (!carrierName.isEmpty()) airlineNames.add(carrierName);
            String number = carrierCode + segment.optString("number", "");
            if (!number.trim().isEmpty()) flightNumbers.add(number.trim());

            JSONObject departure = segment.getJSONObject("departure");
            JSONObject arrival = segment.getJSONObject("arrival");
            segmentText.add(
                    departure.optString("iataCode", "?") + " " + formatDateTime(departure.optString("at", ""))
                            + " → " + arrival.optString("iataCode", "?") + " " + formatDateTime(arrival.optString("at", ""))
            );
        }

        return new Itinerary(
                join(segmentText, " | "),
                Math.max(0, segments.length() - 1),
                parseDurationMinutes(itinerary.optString("duration", "PT0M")),
                airlineNames,
                flightNumbers
        );
    }

    private static String baggageLabel(JSONObject offer) {
        try {
            JSONArray travelerPricings = offer.optJSONArray("travelerPricings");
            if (travelerPricings == null || travelerPricings.length() == 0) return "Baggage not specified";
            JSONArray details = travelerPricings.getJSONObject(0).optJSONArray("fareDetailsBySegment");
            if (details == null || details.length() == 0) return "Baggage not specified";
            JSONObject bags = details.getJSONObject(0).optJSONObject("includedCheckedBags");
            if (bags == null) return "Baggage not specified";
            if (bags.has("quantity")) {
                int quantity = bags.optInt("quantity", 0);
                return quantity + (quantity == 1 ? " checked bag" : " checked bags");
            }
            if (bags.has("weight")) {
                return bags.optInt("weight", 0) + " " + bags.optString("weightUnit", "KG") + " checked baggage";
            }
        } catch (JSONException ignored) {
            // Provider omitted or changed baggage details.
        }
        return "Baggage not specified";
    }

    private static int parseDurationMinutes(String isoDuration) {
        try {
            return Math.toIntExact(Duration.parse(isoDuration).toMinutes());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String formatDateTime(String value) {
        if (value == null || value.isEmpty()) return "time unavailable";
        return value.replace('T', ' ');
    }

    private static String join(Iterable<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(separator);
            result.append(value.trim());
        }
        return result.length() == 0 ? "Provider did not identify carrier" : result.toString();
    }

    private static final class Itinerary {
        final String summary;
        final int stops;
        final int durationMinutes;
        final Set<String> airlineNames;
        final Set<String> flightNumbers;

        Itinerary(String summary, int stops, int durationMinutes, Set<String> airlineNames, Set<String> flightNumbers) {
            this.summary = summary;
            this.stops = stops;
            this.durationMinutes = durationMinutes;
            this.airlineNames = airlineNames;
            this.flightNumbers = flightNumbers;
        }

        static Itinerary empty() {
            return new Itinerary("", 0, 0, Collections.emptySet(), Collections.emptySet());
        }
    }
}
