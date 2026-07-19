package com.flightticketspricetracker;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AirportCatalog {
    private static final Pattern PARENTHESIZED_CODE = Pattern.compile(".*\\(([A-Z]{3})\\).*");
    private static final Pattern LEADING_CODE = Pattern.compile("^([A-Z]{3})\\s*[-–].*");
    private static final Map<String, String> ALIASES;

    private static final String[] SUGGESTIONS = {
            "Ottawa (YOW)", "Montreal Trudeau (YUL)", "Toronto Pearson (YYZ)", "Vancouver (YVR)",
            "Calgary (YYC)", "Edmonton (YEG)", "Halifax (YHZ)", "Winnipeg (YWG)", "Quebec City (YQB)",
            "New York JFK (JFK)", "New York LaGuardia (LGA)", "Newark (EWR)", "Los Angeles (LAX)",
            "San Francisco (SFO)", "Miami (MIA)", "Chicago O'Hare (ORD)", "London Heathrow (LHR)",
            "Paris Charles de Gaulle (CDG)", "Casablanca (CMN)", "Marrakech (RAK)", "Rabat (RBA)",
            "Dubai (DXB)", "Istanbul (IST)", "Madrid (MAD)", "Barcelona (BCN)", "Lisbon (LIS)"
    };

    static {
        Map<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "YOW", "ottawa", "ottawa airport", "ottawa macdonald cartier");
        alias(aliases, "YUL", "montreal", "montreal trudeau", "montreal airport", "montréal", "montréal trudeau");
        alias(aliases, "YYZ", "toronto", "toronto pearson", "pearson");
        alias(aliases, "YVR", "vancouver");
        alias(aliases, "YYC", "calgary");
        alias(aliases, "YEG", "edmonton");
        alias(aliases, "YHZ", "halifax");
        alias(aliases, "YWG", "winnipeg");
        alias(aliases, "YQB", "quebec city", "québec city", "quebec");
        alias(aliases, "JFK", "new york jfk", "john f kennedy", "jfk airport");
        alias(aliases, "LGA", "new york laguardia", "laguardia");
        alias(aliases, "EWR", "newark", "newark liberty");
        alias(aliases, "LAX", "los angeles", "la airport");
        alias(aliases, "SFO", "san francisco", "san francisco airport");
        alias(aliases, "MIA", "miami");
        alias(aliases, "ORD", "chicago", "chicago ohare", "o hare", "ohare");
        alias(aliases, "LHR", "london", "london heathrow", "heathrow");
        alias(aliases, "CDG", "paris", "paris charles de gaulle", "charles de gaulle");
        alias(aliases, "CMN", "casablanca", "mohammed v");
        alias(aliases, "RAK", "marrakech", "marrakesh");
        alias(aliases, "RBA", "rabat");
        alias(aliases, "DXB", "dubai");
        alias(aliases, "IST", "istanbul");
        alias(aliases, "MAD", "madrid");
        alias(aliases, "BCN", "barcelona");
        alias(aliases, "LIS", "lisbon", "lisboa");
        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private AirportCatalog() {}

    public static String resolveCode(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        String upper = trimmed.toUpperCase(Locale.CANADA);
        if (upper.matches("[A-Z]{3}")) return upper;
        Matcher parenthesized = PARENTHESIZED_CODE.matcher(upper);
        if (parenthesized.matches()) return parenthesized.group(1);
        Matcher leading = LEADING_CODE.matcher(upper);
        if (leading.matches()) return leading.group(1);
        return ALIASES.get(normalizeAlias(trimmed));
    }

    public static String[] suggestions() {
        return SUGGESTIONS.clone();
    }

    private static void alias(Map<String, String> aliases, String code, String... names) {
        for (String name : names) aliases.put(normalizeAlias(name), code);
    }

    private static String normalizeAlias(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.CANADA)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized.replaceAll("\\s+", " ");
    }
}
