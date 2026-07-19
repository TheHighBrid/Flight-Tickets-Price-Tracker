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
        String cabinLower = criteria.cabin.toLowerCase(Locale.CANADA);
        double cabinMultiplier = cabinLower.contains("business")
                ? 2.65
                : cabinLower.contains("premium") ? 1.55 : 1.0;
        double tripMultiplier = criteria.roundTrip ? 1.78 : 1.0;
        double currencyMultiplier = "CAD".equals(criteria.currency) ? 1.18 : 1.0;

        List<FareQuote> results = new ArrayList<>();
        for (int i = 0; i < AIRLINES.length; i++) {
            int stops = (seed + i) % 4 == 0 ? 0 : ((seed + i) % 2) + 1;
            int hour = 6 + ((seed / 11 + i * 2) % 13);
            int minute = (i % 2 == 0) ? 15 : 45;
            int duration = 130 + (seed % 210) + i * 19 + stops * 55;
            int arrivalMinutes = hour * 60 + minute + duration;
            String departureTime = String.format(Locale.CANADA, "%02d:%02d", hour, minute);
            String arrivalTime = String.format(Locale.CANADA, "%02d:%02d", (arrivalMinutes / 60) % 24, arrivalMinutes % 60);
            int variability = ((seed >> (i % 8)) & 31) + i * 29 + stops * 34;
            int price = (int) Math.round(
                    (routeFactor + variability)
                            * cabinMultiplier
                            * tripMultiplier
                            * currencyMultiplier
                            * criteria.passengers
            );

            results.add(new FareQuote(
                    AIRLINES[i],
                    criteria.route(),
                    departureTime,
                    arrivalTime,
                    stops,
                    price,
                    criteria.currency,
                    criteria.cabin,
                    duration
            ));
        }
        results.sort(Comparator.comparingInt(quote -> quote.totalPrice));
        return results;
    }

    public int bestPrice(SearchCriteria criteria) {
        List<FareQuote> quotes = search(criteria);
        return quotes.get(0).totalPrice;
    }

    public static String normalizeAirport(String value) {
        String code = AirportCatalog.resolveCode(value);
        return code == null ? "ANY" : code;
    }

    @Deprecated
    public List<FareQuote> search(String origin, String destination, String cabin, boolean roundTrip, int passengers) {
        LocalDate departure = LocalDate.now().plusDays(30);
        LocalDate returning = departure.plusDays(7);
        return search(new SearchCriteria(
                origin,
                destination,
                departure.toString(),
                returning.toString(),
                cabin,
                roundTrip,
                passengers,
                "CAD"
        ));
    }
}
