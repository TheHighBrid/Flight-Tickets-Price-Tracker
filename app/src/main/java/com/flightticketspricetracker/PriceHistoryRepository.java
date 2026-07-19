package com.flightticketspricetracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class PriceHistoryRepository {
    public static final class Entry {
        public final long checkedAt;
        public final String currency;
        public final BigDecimal price;

        Entry(long checkedAt, String currency, BigDecimal price) {
            this.checkedAt = checkedAt;
            this.currency = currency;
            this.price = price;
        }
    }

    private static final String PREFS = "price_history";
    private static final int MAX_ENTRIES = 30;
    private final SharedPreferences preferences;

    public PriceHistoryRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void record(String alertKey, FareQuote quote) {
        List<Entry> history = load(alertKey);
        history.add(0, new Entry(quote.fetchedAtEpochMillis, quote.currency, quote.totalPrice));
        if (history.size() > MAX_ENTRIES) history = new ArrayList<>(history.subList(0, MAX_ENTRIES));
        StringBuilder raw = new StringBuilder();
        for (Entry entry : history) {
            if (raw.length() > 0) raw.append('\n');
            raw.append(entry.checkedAt).append('|').append(entry.currency).append('|').append(entry.price.toPlainString());
        }
        preferences.edit().putString(storageKey(alertKey), raw.toString()).apply();
    }

    public Entry latest(String alertKey) {
        List<Entry> entries = load(alertKey);
        return entries.isEmpty() ? null : entries.get(0);
    }

    public List<Entry> load(String alertKey) {
        List<Entry> entries = new ArrayList<>();
        String raw = preferences.getString(storageKey(alertKey), "");
        if (raw == null || raw.isEmpty()) return entries;
        for (String line : raw.split("\\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3) continue;
            try {
                entries.add(new Entry(Long.parseLong(parts[0]), parts[1], new BigDecimal(parts[2])));
            } catch (RuntimeException ignored) {
                // Skip corrupted history entries.
            }
        }
        return entries;
    }

    public void delete(String alertKey) {
        preferences.edit().remove(storageKey(alertKey)).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private static String storageKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (Exception exception) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
