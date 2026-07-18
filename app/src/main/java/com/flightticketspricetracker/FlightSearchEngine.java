package com.flightticketspricetracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FlightSearchEngine {
    private static final String[] AIRLINES = {"Air Canada", "Royal Air Maroc", "SkyBridge", "Atlantic Air", "PacificJet", "Nimbus"};
    private static final String[] FLIGHTS = {"AC73", "AT208", "SB414", "AA882", "PJ610", "NB928"};

    public List<FareQuote> search(String origin, String destination, String cabin, boolean roundTrip, int passengers) {
        return search(new TripRequest(origin, destination, "2026-08-20", "2027-02-10", cabin, roundTrip, false, passengers, 850));
    }

    public List<FareQuote> search(TripRequest request) {
        int base = routeBasePrice(request.origin, request.destination, request.departDate, request.returnDate);
        double cabinMultiplier = cabinMultiplier(request.cabin);
        double tripMultiplier = request.roundTrip ? 1.82 : 1.0;
        List<FareQuote> results = new ArrayList<>();
        for (int i = 0; i < AIRLINES.length; i++) {
            int stops = directRouteAvailable(request.origin, request.destination) && i < 2 ? 0 : (i % 3) + 1;
            int airlineAdjustment = airlineAdjustment(AIRLINES[i]);
            int price = (int) Math.round((base + airlineAdjustment + (stops * 42)) * cabinMultiplier * tripMultiplier * request.passengers);
            results.add(new FareQuote(AIRLINES[i], FLIGHTS[i], request.origin + " → " + request.destination, departTime(i), arriveTime(i), stops, price, request.cabin, includedBags(AIRLINES[i])));
        }
        results.sort(Comparator.comparingInt(q -> q.priceUsd));
        return results;
    }

    private static int routeBasePrice(String origin, String destination, String departDate, String returnDate) {
        int deterministic = Math.abs((origin + destination + departDate + returnDate).hashCode() % 190);
        int longHaul = (origin.equals("CMN") && destination.equals("YUL")) || (origin.equals("YUL") && destination.equals("CMN")) ? 260 : 150;
        return deterministic + longHaul + 180;
    }

    private static double cabinMultiplier(String cabinName) {
        String lower = cabinName.toLowerCase(Locale.US);
        if (lower.contains("business")) return 2.8;
        if (lower.contains("premium")) return 1.55;
        return 1.0;
    }

    private static int airlineAdjustment(String airline) {
        if (airline.equals("Air Canada")) return 130;
        if (airline.equals("Royal Air Maroc")) return 0;
        return 90;
    }

    private static int includedBags(String airline) {
        if (airline.equals("Air Canada")) return 2;
        if (airline.equals("Royal Air Maroc")) return 1;
        return 1;
    }

    private static boolean directRouteAvailable(String origin, String destination) {
        return (origin.equals("CMN") && destination.equals("YUL")) || (origin.equals("YUL") && destination.equals("CMN"));
    }

    private static String departTime(int index) { return (6 + index * 2) + ":" + (index % 2 == 0 ? "15" : "45"); }
    private static String arriveTime(int index) { return (13 + index * 2) + ":" + (index % 2 == 0 ? "55" : "20"); }

    public static String normalizeAirport(String value) {
        if (value == null || value.trim().isEmpty()) return "ANY";
        String normalized = value.trim().toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        if (normalized.length() >= 3) return normalized.substring(0, 3);
        return String.format(Locale.US, "%-3s", normalized).replace(' ', 'X');
    }
}
