package com.flightticketspricetracker;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.List;

public class MainActivity extends Activity {
    private final FlightSearchEngine engine = new FlightSearchEngine();
    private LinearLayout results;
    private TextView alerts;
    private EditText origin;
    private EditText destination;
    private EditText passengers;
    private EditText target;
    private Spinner cabin;
    private CheckBox roundTrip;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("alerts", MODE_PRIVATE);
        setContentView(buildUi());
        loadAlerts();
        runSearch();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);
        TextView title = label("Flight Tickets Price Tracker", 26, true);
        title.setTextColor(Color.rgb(23,107,135));
        root.addView(title);
        root.addView(label("Find useful fares and save target-price alerts.", 15, false));
        origin = input("Origin airport or city", "JFK"); root.addView(origin);
        destination = input("Destination airport or city", "LAX"); root.addView(destination);
        passengers = input("Passengers", "1"); passengers.setInputType(2); root.addView(passengers);
        cabin = new Spinner(this); cabin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Economy", "Premium Economy", "Business"})); root.addView(cabin);
        roundTrip = new CheckBox(this); roundTrip.setText("Round trip"); roundTrip.setChecked(true); root.addView(roundTrip);
        Button search = new Button(this); search.setText("Search fares"); search.setOnClickListener(v -> runSearch()); root.addView(search);
        target = input("Alert target USD", "350"); target.setInputType(2); root.addView(target);
        Button save = new Button(this); save.setText("Save price alert"); save.setOnClickListener(v -> saveAlert()); root.addView(save);
        alerts = label("", 16, false); root.addView(alerts);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); root.addView(results);
        return scroll;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(sp); view.setPadding(0, 12, 0, 12);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return view;
    }

    private EditText input(String hint, String text) { EditText edit = new EditText(this); edit.setHint(hint); edit.setText(text); edit.setSingleLine(true); return edit; }

    private int parseInt(EditText edit, int fallback) { try { return Integer.parseInt(edit.getText().toString().trim()); } catch (Exception ignored) { return fallback; } }

    private void runSearch() {
        results.removeAllViews();
        int count = parseInt(passengers, 1);
        List<FareQuote> quotes = engine.search(origin.getText().toString(), destination.getText().toString(), String.valueOf(cabin.getSelectedItem()), roundTrip.isChecked(), count);
        results.addView(label("Best fares", 21, true));
        for (FareQuote quote : quotes) {
            TextView row = label(quote.summary(), 16, false);
            row.setGravity(Gravity.START); row.setBackgroundColor(Color.rgb(239, 248, 251)); row.setPadding(18, 18, 18, 18);
            results.addView(row);
        }
    }

    private void saveAlert() {
        PriceAlert alert = new PriceAlert(origin.getText().toString(), destination.getText().toString(), parseInt(target, 350));
        prefs.edit().putString("latest", alert.encode()).apply();
        Toast.makeText(this, "Alert saved: " + alert.summary(), Toast.LENGTH_LONG).show();
        loadAlerts();
    }

    private void loadAlerts() {
        String raw = prefs.getString("latest", null);
        alerts.setText(raw == null ? "No saved alerts yet." : "Saved alert: " + PriceAlert.decode(raw).summary());
    }
}
