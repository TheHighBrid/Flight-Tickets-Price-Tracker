package com.flightticketspricetracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AlertRepository {
    private static final String PREFS_NAME = "alerts";
    private static final String KEY_ALERTS = "saved_live_alerts_v3";
    private static final int MAX_ALERTS = 25;

    private final SharedPreferences preferences;

    public AlertRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<PriceAlert> load() {
        List<PriceAlert> alerts = new ArrayList<>();
        String raw = preferences.getString(KEY_ALERTS, "");
        if (raw == null || raw.trim().isEmpty()) return alerts;
        for (String line : raw.split("\\n")) {
            PriceAlert alert = PriceAlert.tryDecode(line);
            if (alert != null) alerts.add(alert);
        }
        return alerts;
    }

    public void save(PriceAlert alert) {
        List<PriceAlert> alerts = load();
        for (Iterator<PriceAlert> iterator = alerts.iterator(); iterator.hasNext();) {
            if (iterator.next().key().equals(alert.key())) iterator.remove();
        }
        alerts.add(0, alert);
        if (alerts.size() > MAX_ALERTS) alerts = new ArrayList<>(alerts.subList(0, MAX_ALERTS));
        persist(alerts);
    }

    public void delete(String key) {
        List<PriceAlert> alerts = load();
        for (Iterator<PriceAlert> iterator = alerts.iterator(); iterator.hasNext();) {
            if (iterator.next().key().equals(key)) iterator.remove();
        }
        persist(alerts);
    }

    public void clear() {
        preferences.edit().remove(KEY_ALERTS).apply();
    }

    private void persist(List<PriceAlert> alerts) {
        StringBuilder encoded = new StringBuilder();
        for (PriceAlert alert : alerts) {
            if (encoded.length() > 0) encoded.append('\n');
            encoded.append(alert.encode());
        }
        preferences.edit().putString(KEY_ALERTS, encoded.toString()).apply();
    }
}
