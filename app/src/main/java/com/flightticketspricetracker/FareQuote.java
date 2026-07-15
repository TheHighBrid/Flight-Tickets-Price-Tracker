package com.flightticketspricetracker;

import java.util.Objects;

public final class FareQuote {
    public final String airline;
    public final String route;
    public final String departTime;
    public final String arriveTime;
    public final int stops;
    public final int priceUsd;
    public final String cabin;

    public FareQuote(String airline, String route, String departTime, String arriveTime, int stops, int priceUsd, String cabin) {
        this.airline = airline;
        this.route = route;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
        this.stops = stops;
        this.priceUsd = priceUsd;
        this.cabin = cabin;
    }

    public String summary() {
        // Fix: Add null safety checks to prevent NullPointerException
        String safeAirline = Objects.toString(airline, "Unknown");
        String safeRoute = Objects.toString(route, "N/A");
        String safeDepartTime = Objects.toString(departTime, "N/A");
        String safeArriveTime = Objects.toString(arriveTime, "N/A");
        String safeCabin = Objects.toString(cabin, "Unknown");
        
        return safeAirline + " • " + safeRoute + " • " + safeDepartTime + " → " + safeArriveTime + 
               " • " + (stops == 0 ? "Nonstop" : stops + " stop") + " • " + safeCabin + " • $" + priceUsd;
    }
}
