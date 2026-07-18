package com.flightticketspricetracker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FlightSearchEngine {
    private static final String[] AIRLINES = {
            "Northstar Air", "Atlas Connect", "Maple Jet", "Cloudline", "Horizon Wings", "Aero Nova", "Blue Arc"
    };

    public List<FareQuote> search(SearchCriteria criteria) {
        String error = criteria == null ? "Search criteria are required." : criteria.firstValidationError();
        if (error != null) throw new IllegalArgumentException(error);

        int seed = criteria.stableKey().hashCode() & 0x7fffffff;
        int routeFactor = 120 + (seed % 260);
        double cabinMultiplier = criteria.cabin.toLowerCase(Locale.CANADA).contains("business")
                ? 2.65
                : criteria.cabin.toLowerCase(Locale.CANADA).contains("premium") ? 1.55 : 1.0;
        double tripMultiplier = criteria.roundTrip ? 1.78 : 1.0;
        double currencyMultiplier = "CAD".equals(criteria.currency) ? 1.18 : 1.0;

    public List<FareQuote> search(String origin, String destination, String cabin, boolean roundTrip, int passengers) {
        String from = normalizeAirport(origin);
        String to = normalizeAirport(destination);
        String cabinName = cabin == null || cabin.trim().isEmpty() ? "Economy" : cabin.trim();
        int safePassengers = Math.max(1, passengers);
        int base = Math.abs((from + to + cabinName).hashCode() % 250) + 120;
        
        // Fix: Cache toLowerCase() result to avoid redundant calls
        String cabinLower = cabinName.toLowerCase(Locale.US);
        double cabinMultiplier = cabinLower.contains("business") ? 2.8 : 
                                 cabinLower.contains("premium") ? 1.55 : 1.0;
        double tripMultiplier = roundTrip ? 1.82 : 1.0;
        
        // Fix: Build route string once outside the loop
        String route = from + " → " + to;
        
        List<FareQuote> results = new ArrayList<>();
        for (int i = 0; i < AIRLINES.length; i++) {
            int stops = i % 3 == 0 ? 0 : i % 3;
            int price = (int) Math.round((base + (i * 37) + (stops * 26)) * cabinMultiplier * tripMultiplier * safePassengers);
            results.add(new FareQuote(AIRLINES[i], route, (6 + i * 2) + ":" + (i % 2 == 0 ? "15" : "45"), (9 + i * 2) + ":" + (i % 2 == 0 ? "55" : "20"), stops, price, cabinName));
        }
        results.sort(Comparator.comparingInt(quote -> quote.totalPrice));
        return results;
    }

    public int bestPrice(SearchCriteria criteria) {
        List<FareQuote> quotes = search(criteria);
        return quotes.get(0).totalPrice;
    }

    public static String normalizeAirport(String value) {
        if (value == null || value.trim().isEmpty()) return "ANY";
        
        // Fix: Use efficient character filtering instead of regex
        String upper = value.trim().toUpperCase(Locale.US);
        StringBuilder sb = new StringBuilder();
        for (char c : upper.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            }
        }
        String normalized = sb.toString();
        
        if (normalized.length() >= 3) return normalized.substring(0, 3);
        return String.format(Locale.US, "%-3s", normalized).replace(' ', 'X');
    }
}
