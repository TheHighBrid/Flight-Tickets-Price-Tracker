package com.flightticketspricetracker;

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
        return airline + " • " + route + " • " + departTime + " → " + arriveTime + " • " + (stops == 0 ? "Nonstop" : stops + " stop") + " • " + cabin + " • $" + priceUsd;
    }
}
