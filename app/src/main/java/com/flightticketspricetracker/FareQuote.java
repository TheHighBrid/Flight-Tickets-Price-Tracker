package com.flightticketspricetracker;

public final class FareQuote {
    public final String airline;
    public final String flightNumber;
    public final String route;
    public final String departTime;
    public final String arriveTime;
    public final int stops;
    public final int priceUsd;
    public final String cabin;
    public final int checkedBags;

    public FareQuote(String airline, String flightNumber, String route, String departTime, String arriveTime, int stops, int priceUsd, String cabin, int checkedBags) {
        this.airline = airline;
        this.flightNumber = flightNumber;
        this.route = route;
        this.departTime = departTime;
        this.arriveTime = arriveTime;
        this.stops = stops;
        this.priceUsd = priceUsd;
        this.cabin = cabin;
        this.checkedBags = checkedBags;
    }

    public String summary() {
        return airline + " " + flightNumber + " • " + route + " • " + departTime + " → " + arriveTime + " • " + (stops == 0 ? "Nonstop" : stops + " stop") + " • " + cabin + " • " + checkedBags + " checked bag" + (checkedBags == 1 ? "" : "s") + " • $" + priceUsd;
    }
}
