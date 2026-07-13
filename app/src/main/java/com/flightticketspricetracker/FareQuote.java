package com.flightticketspricetracker;

import java.util.Locale;

public final class FareQuote {
    public final String airline;
    public final String route;
    public final String departTime;
    public final String arriveTime;
    public final int stops;
    public final int totalPrice;
    public final String currency;
    public final String cabin;
    public final int durationMinutes;

    public FareQuote(
            String airline,
            String route,
            String departTime,
            String arriveTime,
            int stops,
            int totalPrice,
            String currency,
            String cabin,
            int durationMinutes
    ) {
        this.airline = airline;
        this.route = route;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
        this.stops = Math.max(0, stops);
        this.totalPrice = Math.max(1, totalPrice);
        this.currency = currency;
        this.cabin = cabin;
        this.durationMinutes = Math.max(1, durationMinutes);
    }

    public String stopsLabel() {
        if (stops == 0) return "Nonstop";
        return stops == 1 ? "1 stop" : stops + " stops";
    }

    public String durationLabel() {
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        return String.format(Locale.CANADA, "%dh %02dm", hours, minutes);
    }

    public String priceLabel() {
        return currency + " $" + totalPrice;
    }

    public String summary() {
        return airline + "\n" + route + "  •  " + departTime + " → " + arriveTime
                + "\n" + stopsLabel() + "  •  " + durationLabel() + "  •  " + cabin
                + "\n" + priceLabel() + " total";
    }
}
