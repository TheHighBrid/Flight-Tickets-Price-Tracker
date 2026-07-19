package com.flightticketspricetracker;

import java.math.BigDecimal;

public final class PriceAlert {
    private static final String VERSION = "v3";

    public final SearchCriteria criteria;
    public final BigDecimal targetPrice;

    public PriceAlert(SearchCriteria criteria, BigDecimal targetPrice) {
        if (criteria == null || !criteria.isValid()) {
            throw new IllegalArgumentException("A valid live-flight search is required before saving an alert.");
        }
        if (targetPrice == null || targetPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target price must be greater than zero.");
        }
        this.criteria = criteria;
        this.targetPrice = targetPrice.stripTrailingZeros();
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
                criteria.cabin.replace(" ", "_"),
                Boolean.toString(criteria.roundTrip),
                Boolean.toString(criteria.nonStop),
                Integer.toString(criteria.passengers),
                criteria.currency,
                targetPrice.toPlainString()
        );
    }

    public static PriceAlert decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("Invalid alert");
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 11 || !VERSION.equals(parts[0])) throw new IllegalArgumentException("Unsupported alert format");
        SearchCriteria criteria = new SearchCriteria(
                parts[1], parts[2], parts[3], parts[4], parts[5].replace('_', ' '),
                Boolean.parseBoolean(parts[6]), Boolean.parseBoolean(parts[7]),
                Integer.parseInt(parts[8]), parts[9]
        );
        return new PriceAlert(criteria, new BigDecimal(parts[10]));
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
                + (criteria.nonStop ? " • nonstop only" : "")
                + " • target " + criteria.currency + " $" + targetPrice.toPlainString();
    }
}
