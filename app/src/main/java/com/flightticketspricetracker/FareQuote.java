package com.flightticketspricetracker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class FareQuote implements Comparable<FareQuote> {
    public final String offerId;
    public final String airlineNames;
    public final String flightNumbers;
    public final String route;
    public final String outbound;
    public final String inbound;
    public final int stops;
    public final int durationMinutes;
    public final BigDecimal totalPrice;
    public final String currency;
    public final String baggage;
    public final String provider;
    public final String environment;
    public final long fetchedAtEpochMillis;

    public FareQuote(
            String offerId,
            String airlineNames,
            String flightNumbers,
            String route,
            String outbound,
            String inbound,
            int stops,
            int durationMinutes,
            BigDecimal totalPrice,
            String currency,
            String baggage,
            String provider,
            String environment,
            long fetchedAtEpochMillis
    ) {
        this.offerId = safe(offerId, "offer");
        this.airlineNames = safe(airlineNames, "Unknown carrier");
        this.flightNumbers = safe(flightNumbers, "Flight number unavailable");
        this.route = safe(route, "Route unavailable");
        this.outbound = safe(outbound, "Outbound itinerary unavailable");
        this.inbound = inbound == null ? "" : inbound;
        this.stops = Math.max(0, stops);
        this.durationMinutes = Math.max(0, durationMinutes);
        this.totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice.setScale(2, RoundingMode.HALF_UP);
        this.currency = safe(currency, "CAD");
        this.baggage = safe(baggage, "Baggage not specified by provider");
        this.provider = safe(provider, "Provider");
        this.environment = safe(environment, "unknown");
        this.fetchedAtEpochMillis = fetchedAtEpochMillis;
    }

    public String priceLabel() {
        return currency + " $" + totalPrice.toPlainString();
    }

    public String stopsLabel() {
        if (stops == 0) return "Nonstop";
        return stops == 1 ? "1 stop" : stops + " stops";
    }

    public String durationLabel() {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        return hours + "h " + String.format(Locale.CANADA, "%02dm", minutes);
    }

    public String verifiedLabel() {
        return "Checked " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(fetchedAtEpochMillis));
    }

    public String summary() {
        StringBuilder text = new StringBuilder();
        text.append(airlineNames).append("\n")
                .append(flightNumbers).append(" • ").append(route).append("\n")
                .append("Outbound: ").append(outbound).append("\n");
        if (!inbound.isEmpty()) text.append("Return: ").append(inbound).append("\n");
        text.append(stopsLabel()).append(" • ").append(durationLabel()).append(" • ").append(baggage).append("\n")
                .append(priceLabel()).append(" total • ").append(provider).append(" ").append(environment).append("\n")
                .append(verifiedLabel());
        return text.toString();
    }

    @Override
    public int compareTo(FareQuote other) {
        return totalPrice.compareTo(other.totalPrice);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
