package com.flightticketspricetracker;

import java.net.URLDecoder;
import java.net.URLEncoder;

public final class ProviderConfig {
    public enum Mode { AMADEUS_DIRECT, BACKEND }
    public enum Environment { TEST, PRODUCTION }

    public final Mode mode;
    public final Environment environment;
    public final String apiKey;
    public final String apiSecret;
    public final String backendUrl;
    public final String backendToken;

    public ProviderConfig(
            Mode mode,
            Environment environment,
            String apiKey,
            String apiSecret,
            String backendUrl,
            String backendToken
    ) {
        this.mode = mode == null ? Mode.AMADEUS_DIRECT : mode;
        this.environment = environment == null ? Environment.TEST : environment;
        this.apiKey = clean(apiKey);
        this.apiSecret = clean(apiSecret);
        this.backendUrl = trimSlash(clean(backendUrl));
        this.backendToken = clean(backendToken);
    }

    public static ProviderConfig empty(String defaultBackendUrl) {
        return new ProviderConfig(Mode.BACKEND, Environment.PRODUCTION, "", "", defaultBackendUrl, "");
    }

    public boolean isConfigured() {
        if (mode == Mode.BACKEND) return backendUrl.startsWith("https://");
        return !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    public String validationError() {
        if (mode == Mode.BACKEND) {
            if (backendUrl.isEmpty()) return "Enter the HTTPS URL of the flight backend.";
            if (!backendUrl.startsWith("https://")) return "The backend URL must use HTTPS.";
            return null;
        }
        if (apiKey.isEmpty()) return "Enter the Amadeus API key.";
        if (apiSecret.isEmpty()) return "Enter the Amadeus API secret.";
        return null;
    }

    public String environmentLabel() {
        return environment == Environment.PRODUCTION ? "production" : "test";
    }

    public String statusLabel() {
        if (!isConfigured()) return "NOT CONFIGURED • No searches will run";
        if (mode == Mode.BACKEND) return "SECURE BACKEND • " + backendUrl;
        if (environment == Environment.PRODUCTION) return "LIVE PRODUCTION INVENTORY • Amadeus";
        return "AMADEUS TEST ENVIRONMENT • Limited provider test data, not live inventory";
    }

    public String encode() {
        return String.join("|", "v1", mode.name(), environment.name(),
                encoded(apiKey), encoded(apiSecret), encoded(backendUrl), encoded(backendToken));
    }

    public static ProviderConfig decode(String raw, String defaultBackendUrl) {
        if (raw == null || raw.trim().isEmpty()) return empty(defaultBackendUrl);
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 7 || !"v1".equals(parts[0])) return empty(defaultBackendUrl);
        try {
            return new ProviderConfig(
                    Mode.valueOf(parts[1]), Environment.valueOf(parts[2]),
                    decoded(parts[3]), decoded(parts[4]), decoded(parts[5]), decoded(parts[6])
            );
        } catch (RuntimeException ignored) {
            return empty(defaultBackendUrl);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String encoded(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String decoded(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
