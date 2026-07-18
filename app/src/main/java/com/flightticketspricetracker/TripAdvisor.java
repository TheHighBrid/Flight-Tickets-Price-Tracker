package com.flightticketspricetracker;

import java.util.ArrayList;
import java.util.List;

public final class TripAdvisor {
    private static final int AIR_CANADA_ACCEPTABLE_PREMIUM_USD = 260;

    public List<FareQuote> prioritize(TripRequest request, List<FareQuote> quotes) {
        List<FareQuote> filtered = new ArrayList<>();
        for (FareQuote quote : quotes) {
            if (!request.directOnly || quote.stops == 0) filtered.add(quote);
        }
        filtered.sort((left, right) -> score(request, left) - score(request, right));
        return filtered;
    }

    public String recommendation(TripRequest request, List<FareQuote> prioritized) {
        if (!request.isVisaSafeReturnWindow()) {
            return "Return date must be earlier than the exact 6-month mark for visa safety.";
        }
        if (prioritized.isEmpty()) {
            return "No direct demo fares found. Try changing dates or disabling direct-only.";
        }
        FareQuote best = prioritized.get(0);
        String baggage = best.airline.equals("Air Canada") ? "Air Canada preferred: 2 checked bags on the Casablanca route." : best.airline.equals("Royal Air Maroc") ? "Royal Air Maroc selected because it is considerably cheaper; verify baggage before booking." : "Alternative direct fare selected.";
        return "Recommended: " + best.summary() + ". " + baggage;
    }

    private int score(TripRequest request, FareQuote quote) {
        int score = quote.priceUsd;
        if (quote.airline.equals("Air Canada")) score -= AIR_CANADA_ACCEPTABLE_PREMIUM_USD;
        if (!quote.airline.equals("Air Canada") && !quote.airline.equals("Royal Air Maroc")) score += 120;
        if (request.directOnly && quote.stops > 0) score += 5000;
        if (quote.priceUsd <= request.targetUsd) score -= 60;
        return score;
    }
}
