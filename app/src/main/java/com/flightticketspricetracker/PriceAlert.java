package com.flightticketspricetracker;

import java.time.LocalDate;

public final class PriceAlert {
    private static final String VERSION = "v2";

    public final SearchCriteria criteria;
    public final int targetPrice;

    public PriceAlert(SearchCriteria criteria, int targetPrice) {
        if (criteria == null || !criteria.isValid()) throw new IllegalArgumentException("A valid search is required before saving an alert.");
        if (targetPrice < 1) throw new IllegalArgumentException("Target price must be greater than zero.");
        this.criteria = criteria;
        this.targetPrice = targetPrice;
    }

    @Deprecated
    public PriceAlert(String origin, String destination, int targetUsd) {
        this(new SearchCriteria(
                origin,
                destination,
                LocalDate.now().plusDays(30).toString(),
                LocalDate.now().plusDays(37).toString(),
                "Economy",
                true,
                1,
                "USD"
        ), targetUsd);
    }

    public String key() {
        return criteria.stableKey();
    }

    public String encode() {
        return String.join("|",
                VERSION,
                criteria.origin,
                criteria.destination,
                criteria.departureDate,
                criteria.roundTrip ? criteria.returnDate : "",
                criteria.cabin,
                Boolean.toString(criteria.roundTrip),
                Integer.toString(criteria.passengers),
                criteria.currency,
                Integer.toString(targetPrice)
        );
    }

    public static PriceAlert decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Invalid alert");
        if (raw.startsWith(VERSION + "|")) {
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 10) throw new IllegalArgumentException("Invalid v2 alert");
            SearchCriteria criteria = new SearchCriteria(
                    parts[1], parts[2], parts[3], parts[4], parts[5],
                    Boolean.parseBoolean(parts[6]), Integer.parseInt(parts[7]), parts[8]
            );
            return new PriceAlert(criteria, Integer.parseInt(parts[9]));
        }

        String[] legacy = raw.split(",");
        if (legacy.length == 3) return new PriceAlert(legacy[0], legacy[1], Integer.parseInt(legacy[2]));
        throw new IllegalArgumentException("Invalid alert");
    }

    public static PriceAlert tryDecode(String raw) {
        try {
            return decode(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public String summary() {
        String dates = criteria.departureDate + (criteria.roundTrip ? " to " + criteria.returnDate : "");
        return criteria.route() + " • " + dates + " • " + criteria.cabin + " • "
                + criteria.passengers + (criteria.passengers == 1 ? " traveller" : " travellers")
                + " • below " + criteria.currency + " $" + targetPrice;
    }
}
