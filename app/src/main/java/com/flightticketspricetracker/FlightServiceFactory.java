package com.flightticketspricetracker;

public final class FlightServiceFactory {
    private FlightServiceFactory() {}

    public static FlightService create(ProviderConfig config) {
        return create(config, null);
    }

    public static FlightService create(ProviderConfig config, SecureConfigStore configStore) {
        if (config.mode == ProviderConfig.Mode.BACKEND) return new BackendFlightService(config);
        return new SerpApiFlightService(config, configStore);
    }
}
