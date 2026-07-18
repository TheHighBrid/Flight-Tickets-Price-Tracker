package com.flightticketspricetracker;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SearchCriteria {
    public final String origin;
    public final String destination;
    public final String departureDate;
    public final String returnDate;
    public final String cabin;
    public final boolean roundTrip;
    public final int passengers;
    public final String currency;

    public SearchCriteria(
            String origin,
            String destination,
            String departureDate,
            String returnDate,
            String cabin,
            boolean roundTrip,
            int passengers,
            String currency
    ) {
        this.origin = AirportCatalog.resolveCode(origin);
        this.destination = AirportCatalog.resolveCode(destination);
        this.departureDate = clean(departureDate);
        this.returnDate = clean(returnDate);
        this.cabin = normalizeCabin(cabin);
        this.roundTrip = roundTrip;
        this.passengers = passengers;
        this.currency = normalizeCurrency(currency);
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (origin == null) errors.add("Choose a supported origin airport or enter a valid 3-letter IATA code.");
        if (destination == null) errors.add("Choose a supported destination airport or enter a valid 3-letter IATA code.");
        if (origin != null && origin.equals(destination)) errors.add("Origin and destination must be different.");
        if (passengers < 1 || passengers > 9) errors.add("Passengers must be between 1 and 9.");

        LocalDate departure = parseDate(departureDate);
        if (departure == null) {
            errors.add("Departure date must use YYYY-MM-DD.");
        } else if (departure.isBefore(LocalDate.now())) {
            errors.add("Departure date cannot be in the past.");
        }

        if (roundTrip) {
            LocalDate returning = parseDate(returnDate);
            if (returning == null) {
                errors.add("Return date must use YYYY-MM-DD for a round trip.");
            } else if (departure != null && returning.isBefore(departure)) {
                errors.add("Return date cannot be before departure.");
            }
        }
        return Collections.unmodifiableList(errors);
    }

    public boolean isValid() {
        return validationErrors().isEmpty();
    }

    public String firstValidationError() {
        List<String> errors = validationErrors();
        return errors.isEmpty() ? null : errors.get(0);
    }

    public String route() {
        return (origin == null ? "???" : origin) + " → " + (destination == null ? "???" : destination);
    }

    public String stableKey() {
        return route() + "|" + departureDate + "|" + (roundTrip ? returnDate : "") + "|" + cabin + "|" + roundTrip + "|" + passengers + "|" + currency;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeCabin(String value) {
        String cabin = clean(value);
        if (cabin.isEmpty()) return "Economy";
        String lower = cabin.toLowerCase(Locale.CANADA);
        if (lower.contains("business")) return "Business";
        if (lower.contains("premium")) return "Premium Economy";
        return "Economy";
    }

    private static String normalizeCurrency(String value) {
        String currency = clean(value).toUpperCase(Locale.CANADA);
        return "USD".equals(currency) ? "USD" : "CAD";
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException | NullPointerException ignored) {
            return null;
        }
    }
}
