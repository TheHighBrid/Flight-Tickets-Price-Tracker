package com.flightticketspricetracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FlightSearchEngine {
    private static final String[] AIRLINES = {"SkyBridge", "Atlantic Air", "PacificJet", "Nimbus", "Liberty Wings"};

    public List<FareQuote> search(String origin, String destination, String cabin, boolean roundTrip, int passengers) {
        String from = normalizeAirport(origin);
        String to = normalizeAirport(destination);
        String cabinName = cabin == null || cabin.trim().isEmpty() ? "Economy" : cabin.trim();
        int safePassengers = Math.max(1, passengers);
        int base = Math.abs((from + to + cabinName).hashCode() % 250) + 120;
        double cabinMultiplier = cabinName.toLowerCase(Locale.US).contains("business") ? 2.8 : cabinName.toLowerCase(Locale.US).contains("premium") ? 1.55 : 1.0;
        double tripMultiplier = roundTrip ? 1.82 : 1.0;
        List<FareQuote> results = new ArrayList<>();
        for (int i = 0; i < AIRLINES.length; i++) {
            int stops = i % 3 == 0 ? 0 : i % 3;
            int price = (int) Math.round((base + (i * 37) + (stops * 26)) * cabinMultiplier * tripMultiplier * safePassengers);
            results.add(new FareQuote(AIRLINES[i], from + " → " + to, (6 + i * 2) + ":" + (i % 2 == 0 ? "15" : "45"), (9 + i * 2) + ":" + (i % 2 == 0 ? "55" : "20"), stops, price, cabinName));
        }
        results.sort(Comparator.comparingInt(q -> q.priceUsd));
        return results;
    }

    public static String normalizeAirport(String value) {
        if (value == null || value.trim().isEmpty()) return "ANY";
        String normalized = value.trim().toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        if (normalized.length() >= 3) return normalized.substring(0, 3);
        return String.format(Locale.US, "%-3s", normalized).replace(' ', 'X');
    }
}
