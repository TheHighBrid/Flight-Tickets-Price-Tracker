package com.flightticketspricetracker;

import java.net.URLDecoder;
import java.net.URLEncoder;

public final class ProviderConfig {
    public enum Mode { SERPAPI_DIRECT, BACKEND }

    // Kept in the serialized model so older installs can be invalidated safely.
    // SerpApi itself does not have separate test/production hosts.
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
        this.mode = mode == null ? Mode.SERPAPI_DIRECT : mode;
        this.environment = environment == null ? Environment.PRODUCTION : environment;
        this.apiKey = clean(apiKey);
        this.apiSecret = ""; // SerpApi uses one API key only.
        this.backendUrl = trimSlash(clean(backendUrl));
        this.backendToken = clean(backendToken);
    }

    public static ProviderConfig empty(String defaultBackendUrl) {
        return new ProviderConfig(Mode.SERPAPI_DIRECT, Environment.PRODUCTION, "", "", defaultBackendUrl, "");
    }

    public boolean isConfigured() {
        if (mode == Mode.BACKEND) return backendUrl.startsWith("https://");
        return !apiKey.isEmpty();
    }

    public String validationError() {
        if (mode == Mode.BACKEND) {
            if (backendUrl.isEmpty()) return "Enter the HTTPS URL of the flight backend.";
            if (!backendUrl.startsWith("https://")) return "The backend URL must use HTTPS.";
            return null;
        }
        if (apiKey.isEmpty()) return "Enter your SerpApi API key.";
        return null;
    }

    public String environmentLabel() {
        return "cache-enabled";
    }

    public String statusLabel() {
        if (!isConfigured()) return "NOT CONFIGURED • Add a free SerpApi key";
        if (mode == Mode.BACKEND) return "SECURE BACKEND • " + backendUrl;
        return "GOOGLE FLIGHTS VIA SERPAPI • API key stored on this device";
    }

    public String encode() {
        return String.join("|", "v2", mode.name(), environment.name(),
                encoded(apiKey), encoded(""), encoded(backendUrl), encoded(backendToken));
    }

    public static ProviderConfig decode(String raw, String defaultBackendUrl) {
        if (raw == null || raw.trim().isEmpty()) return empty(defaultBackendUrl);
        String[] parts = raw.split("\\|", -1);
        // v1 contained Amadeus credentials. Do not silently reuse them with a different provider.
        if (parts.length != 7 || !"v2".equals(parts[0])) return empty(defaultBackendUrl);
        try {
            return new ProviderConfig(
                    Mode.valueOf(parts[1]), Environment.valueOf(parts[2]),
                    decoded(parts[3]), "", decoded(parts[5]), decoded(parts[6])
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
