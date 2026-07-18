package com.flightticketspricetracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BRAND = Color.rgb(23, 107, 135);
    private static final int INK = Color.rgb(24, 35, 39);
    private static final int MUTED = Color.rgb(88, 105, 111);
    private static final int SURFACE = Color.rgb(239, 248, 251);
    private static final int WARNING = Color.rgb(255, 247, 221);

    private final FlightSearchEngine engine = new FlightSearchEngine();
    private final AlertEvaluator alertEvaluator = new AlertEvaluator(engine);

    private AlertRepository alertRepository;
    private AutoCompleteTextView origin;
    private AutoCompleteTextView destination;
    private EditText departureDate;
    private EditText returnDate;
    private EditText passengers;
    private EditText targetPrice;
    private Spinner cabin;
    private Spinner currency;
    private CheckBox roundTrip;
    private LinearLayout resultsContainer;
    private LinearLayout alertsContainer;
    private TextView returnDateLabel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        alertRepository = new AlertRepository(this);
        setContentView(buildUi());
        setInitialDates();
        renderAlerts(Collections.emptyList());
        runSearch();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(40));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Flight Tickets Price Tracker", 27, true, INK);
        root.addView(title);
        TextView subtitle = text("Search demo fares, compare totals, and track price targets locally.", 15, false, MUTED);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        TextView demoNotice = text(
                "DEMO MODE • Prices are generated on-device and are not live airline fares. Connect a secure backend provider before using this app for purchase decisions.",
                13,
                true,
                Color.rgb(93, 69, 7)
        );
        demoNotice.setBackground(cardBackground(WARNING, Color.rgb(232, 203, 118)));
        demoNotice.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(demoNotice, spacedParams(0, dp(12)));

        root.addView(sectionTitle("Search"));
        origin = airportInput("Origin airport", "Ottawa (YOW)");
        root.addView(fieldLabel("Origin"));
        root.addView(origin);

        destination = airportInput("Destination airport", "Casablanca (CMN)");
        root.addView(fieldLabel("Destination"));
        root.addView(destination);

        departureDate = dateInput();
        root.addView(fieldLabel("Departure date"));
        root.addView(departureDate);

        returnDateLabel = fieldLabel("Return date");
        root.addView(returnDateLabel);
        returnDate = dateInput();
        root.addView(returnDate);

        passengers = standardInput("1");
        passengers.setHint("1 to 9");
        passengers.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(fieldLabel("Passengers"));
        root.addView(passengers);

        cabin = spinner(new String[]{"Economy", "Premium Economy", "Business"});
        root.addView(fieldLabel("Cabin"));
        root.addView(cabin);

        currency = spinner(new String[]{"CAD", "USD"});
        root.addView(fieldLabel("Display currency"));
        root.addView(currency);

        roundTrip = new CheckBox(this);
        roundTrip.setText("Round trip");
        roundTrip.setTextColor(INK);
        roundTrip.setTextSize(16);
        roundTrip.setChecked(true);
        roundTrip.setPadding(0, dp(8), 0, dp(8));
        roundTrip.setOnCheckedChangeListener((buttonView, checked) -> {
            int visibility = checked ? View.VISIBLE : View.GONE;
            returnDateLabel.setVisibility(visibility);
            returnDate.setVisibility(visibility);
        });
        root.addView(roundTrip);

        Button searchButton = primaryButton("Search demo fares");
        searchButton.setOnClickListener(view -> runSearch());
        root.addView(searchButton, spacedParams(dp(4), dp(10)));

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultsContainer);

        root.addView(sectionTitle("Price alerts"));
        targetPrice = standardInput("700");
        targetPrice.setHint("Target total price");
        targetPrice.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(fieldLabel("Target total price"));
        root.addView(targetPrice);

        LinearLayout alertActions = new LinearLayout(this);
        alertActions.setOrientation(LinearLayout.HORIZONTAL);
        alertActions.setGravity(Gravity.CENTER_VERTICAL);

        Button saveAlert = primaryButton("Save alert");
        saveAlert.setOnClickListener(view -> saveAlert());
        alertActions.addView(saveAlert, weightedButtonParams(1f, dp(6)));

        Button checkAlerts = secondaryButton("Check now");
        checkAlerts.setOnClickListener(view -> checkAlertsNow());
        alertActions.addView(checkAlerts, weightedButtonParams(1f, 0));
        root.addView(alertActions, spacedParams(dp(4), dp(12)));

        Button clearAlerts = secondaryButton("Clear all alerts");
        clearAlerts.setOnClickListener(view -> confirmClearAlerts());
        root.addView(clearAlerts, spacedParams(0, dp(10)));

        alertsContainer = new LinearLayout(this);
        alertsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(alertsContainer);

        return scroll;
    }

    private void setInitialDates() {
        LocalDate departure = LocalDate.now().plusDays(30);
        departureDate.setText(departure.toString());
        returnDate.setText(departure.plusDays(7).toString());
    }

    private void runSearch() {
        SearchCriteria criteria = readCriteria();
        String error = criteria.firstValidationError();
        if (error != null) {
            showMessage(error);
            return;
        }

        List<FareQuote> quotes;
        try {
            quotes = engine.search(criteria);
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
            return;
        }

        resultsContainer.removeAllViews();
        TextView heading = text("Best demo fares", 21, true, INK);
        heading.setPadding(0, dp(18), 0, dp(4));
        resultsContainer.addView(heading);
        TextView detail = text(criteria.route() + " • " + criteria.departureDate
                + (criteria.roundTrip ? " to " + criteria.returnDate : "")
                + " • " + criteria.passengers + (criteria.passengers == 1 ? " traveller" : " travellers"), 14, false, MUTED);
        detail.setPadding(0, 0, 0, dp(8));
        resultsContainer.addView(detail);

    private void runSearch() {
        int count = parseInt(passengers, 1);
        List<FareQuote> quotes = engine.search(origin.getText().toString(), destination.getText().toString(), String.valueOf(cabin.getSelectedItem()), roundTrip.isChecked(), count);
        
        // Fix: Implement efficient view recycling instead of removing all views
        // Keep the title (first child) and remove only the old search results
        int childCount = results.getChildCount();
        if (childCount > 1) {
            results.removeViews(1, childCount - 1);
        }
        
        // Add title only if it doesn't exist
        if (childCount == 0) {
            results.addView(label("Best fares", 21, true));
        }
        
        // Add new results
        for (FareQuote quote : quotes) {
            TextView row = label(quote.summary(), 16, false);
            row.setGravity(Gravity.START); 
            row.setBackgroundColor(Color.rgb(239, 248, 251)); 
            row.setPadding(18, 18, 18, 18);
            results.addView(row);
        }
    }

    private void saveAlert() {
        PriceAlert alert = new PriceAlert(origin.getText().toString(), destination.getText().toString(), parseInt(target, 350));
        // Fix: Use background thread for SharedPreferences to avoid blocking main thread
        new Thread(() -> {
            prefs.edit().putString("latest", alert.encode()).apply();
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Alert saved: " + alert.summary(), Toast.LENGTH_LONG).show();
                loadAlerts();
            });
        }).start();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message == null ? "Something went wrong." : message, Toast.LENGTH_LONG).show();
    }
}
