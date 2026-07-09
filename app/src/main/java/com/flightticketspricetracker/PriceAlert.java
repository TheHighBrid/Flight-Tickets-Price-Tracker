package com.flightticketspricetracker;

public final class PriceAlert {
    public final String origin;
    public final String destination;
    public final int targetUsd;

    public PriceAlert(String origin, String destination, int targetUsd) {
        this.origin = FlightSearchEngine.normalizeAirport(origin);
        this.destination = FlightSearchEngine.normalizeAirport(destination);
        this.targetUsd = Math.max(1, targetUsd);
    }

    public String encode() { return origin + "," + destination + "," + targetUsd; }
    public static PriceAlert decode(String raw) {
        String[] parts = raw.split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid alert");
        return new PriceAlert(parts[0], parts[1], Integer.parseInt(parts[2]));
    }

    public String summary() { return origin + " → " + destination + " below $" + targetUsd; }
}
