package com.flightticketspricetracker;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProviderConfig {
    public static final int MAX_SERPAPI_KEYS = 5;

    public enum Mode { SERPAPI_DIRECT, BACKEND }

    // Kept in the serialized model so older installs can be invalidated safely.
    // SerpApi itself does not have separate test/production hosts.
    public enum Environment { TEST, PRODUCTION }

    public final Mode mode;
    public final Environment environment;
    public final List<String> apiKeys;
    // Compatibility alias for code that still expects the first SerpApi key.
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
        this(mode, environment, new String[]{apiKey}, apiSecret, backendUrl, backendToken);
    }

    public ProviderConfig(
            Mode mode,
            Environment environment,
            String[] apiKeys,
            String apiSecret,
            String backendUrl,
            String backendToken
    ) {
        this.mode = mode == null ? Mode.SERPAPI_DIRECT : mode;
        this.environment = environment == null ? Environment.PRODUCTION : environment;
        this.apiKeys = normalizeKeys(apiKeys);
        this.apiKey = this.apiKeys.isEmpty() ? "" : this.apiKeys.get(0);
        this.apiSecret = ""; // SerpApi uses API keys only.
        this.backendUrl = trimSlash(clean(backendUrl));
        this.backendToken = clean(backendToken);
    }

    public static ProviderConfig empty(String defaultBackendUrl) {
        return new ProviderConfig(Mode.SERPAPI_DIRECT, Environment.PRODUCTION, new String[0], "", defaultBackendUrl, "");
    }

    public boolean isConfigured() {
        if (mode == Mode.BACKEND) return backendUrl.startsWith("https://");
        return !apiKeys.isEmpty();
    }

    public String validationError() {
        if (mode == Mode.BACKEND) {
            if (backendUrl.isEmpty()) return "Enter the HTTPS URL of the flight backend.";
            if (!backendUrl.startsWith("https://")) return "The backend URL must use HTTPS.";
            return null;
        }
        if (apiKeys.isEmpty()) return "Enter at least one SerpApi API key.";
        return null;
    }

    public int apiKeyCount() {
        return apiKeys.size();
    }

    public String apiKeyAt(int index) {
        if (index < 0 || index >= apiKeys.size()) return "";
        return apiKeys.get(index);
    }

    public String environmentLabel() {
        return "cache-enabled";
    }

    public String statusLabel() {
        if (!isConfigured()) return "NOT CONFIGURED • Add a free SerpApi key";
        if (mode == Mode.BACKEND) return "SECURE BACKEND • " + backendUrl;
        int count = apiKeys.size();
        return "GOOGLE FLIGHTS VIA SERPAPI • " + count + " encrypted key" + (count == 1 ? "" : "s");
    }

    public String encode() {
        return String.join("|", "v3", mode.name(), environment.name(),
                encoded(String.join("\n", apiKeys)), encoded(""), encoded(backendUrl), encoded(backendToken));
    }

    public static ProviderConfig decode(String raw, String defaultBackendUrl) {
        if (raw == null || raw.trim().isEmpty()) return empty(defaultBackendUrl);
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 7) return empty(defaultBackendUrl);
        try {
            if ("v3".equals(parts[0])) {
                return new ProviderConfig(
                        Mode.valueOf(parts[1]), Environment.valueOf(parts[2]),
                        decoded(parts[3]).split("\\n", -1), "", decoded(parts[5]), decoded(parts[6])
                );
            }
            // v2 stored one SerpApi key. Migrate it into key slot 1 without losing it.
            if ("v2".equals(parts[0])) {
                return new ProviderConfig(
                        Mode.valueOf(parts[1]), Environment.valueOf(parts[2]),
                        decoded(parts[3]), "", decoded(parts[5]), decoded(parts[6])
                );
            }
            // v1 contained Amadeus credentials. Do not silently reuse them with a different provider.
            return empty(defaultBackendUrl);
        } catch (RuntimeException ignored) {
            return empty(defaultBackendUrl);
        }
    }

    private static List<String> normalizeKeys(String[] values) {
        Set<String> unique = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String cleaned = clean(value);
                if (cleaned.isEmpty()) continue;
                unique.add(cleaned);
                if (unique.size() == MAX_SERPAPI_KEYS) break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unique));
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
