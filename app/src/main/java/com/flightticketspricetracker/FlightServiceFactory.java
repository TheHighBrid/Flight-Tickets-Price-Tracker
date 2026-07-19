package com.flightticketspricetracker;

public final class FlightServiceFactory {
    private FlightServiceFactory() {}

    public static FlightService create(ProviderConfig config) {
        if (config.mode == ProviderConfig.Mode.BACKEND) return new BackendFlightService(config);
        return new AmadeusFlightService(config);
    }
}
