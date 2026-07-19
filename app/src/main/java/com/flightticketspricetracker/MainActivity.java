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

        root.addView(text("Flight Tickets Price Tracker", 27, true, INK));
        TextView subtitle = text("Search demo fares, compare totals, and track price targets locally.", 15, false, MUTED);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        TextView demoNotice = text(
                "DEMO MODE • Prices are generated on-device and are not live airline fares.",
                13,
                true,
                Color.rgb(93, 69, 7)
        );
        demoNotice.setBackground(cardBackground(WARNING, Color.rgb(232, 203, 118)));
        demoNotice.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(demoNotice, spacedParams(0, dp(12)));

        root.addView(sectionTitle("Search"));

        root.addView(fieldLabel("Origin"));
        origin = airportInput("Origin airport", "Ottawa (YOW)");
        root.addView(origin);

        root.addView(fieldLabel("Destination"));
        destination = airportInput("Destination airport", "Casablanca (CMN)");
        root.addView(destination);

        root.addView(fieldLabel("Departure date"));
        departureDate = dateInput();
        root.addView(departureDate);

        returnDateLabel = fieldLabel("Return date");
        root.addView(returnDateLabel);
        returnDate = dateInput();
        root.addView(returnDate);

        root.addView(fieldLabel("Passengers"));
        passengers = standardInput("1");
        passengers.setHint("1 to 9");
        passengers.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(passengers);

        root.addView(fieldLabel("Cabin"));
        cabin = spinner(new String[]{"Economy", "Premium Economy", "Business"});
        root.addView(cabin);

        root.addView(fieldLabel("Display currency"));
        currency = spinner(new String[]{"CAD", "USD"});
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
        root.addView(fieldLabel("Target total price"));
        targetPrice = standardInput("700");
        targetPrice.setHint("Target total price");
        targetPrice.setInputType(InputType.TYPE_CLASS_NUMBER);
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

        String tripDates = criteria.departureDate + (criteria.roundTrip ? " to " + criteria.returnDate : "");
        TextView detail = text(
                criteria.route() + " • " + tripDates + " • " + criteria.passengers
                        + (criteria.passengers == 1 ? " traveller" : " travellers"),
                14,
                false,
                MUTED
        );
        detail.setPadding(0, 0, 0, dp(8));
        resultsContainer.addView(detail);

        for (FareQuote quote : quotes) {
            TextView row = text(quote.summary(), 16, false, INK);
            row.setLineSpacing(0, 1.08f);
            row.setBackground(cardBackground(SURFACE, Color.rgb(198, 225, 234)));
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setOnClickListener(view -> {
                targetPrice.setText(Integer.toString(quote.totalPrice));
                showMessage("Target set to " + quote.priceLabel() + ".");
            });
            resultsContainer.addView(row, spacedParams(dp(4), dp(6)));
        }
    }

    private void saveAlert() {
        SearchCriteria criteria = readCriteria();
        String error = criteria.firstValidationError();
        if (error != null) {
            showMessage(error);
            return;
        }

        int target = parsePositiveInt(targetPrice, -1);
        if (target < 1) {
            targetPrice.setError("Enter a target greater than zero.");
            return;
        }

        try {
            alertRepository.save(new PriceAlert(criteria, target));
            renderAlerts(Collections.emptyList());
            showMessage("Alert saved locally.");
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void checkAlertsNow() {
        List<PriceAlert> alerts = alertRepository.load();
        if (alerts.isEmpty()) {
            showMessage("Save an alert first.");
            return;
        }

        List<AlertEvaluator.Result> evaluations = alertEvaluator.evaluate(alerts);
        renderAlerts(evaluations);
        int reached = 0;
        for (AlertEvaluator.Result result : evaluations) {
            if (result.targetReached) reached++;
        }
        showMessage(reached == 0
                ? "No demo targets reached."
                : reached + " demo target" + (reached == 1 ? " reached." : "s reached."));
    }

    private void renderAlerts(List<AlertEvaluator.Result> evaluations) {
        if (alertsContainer == null) return;
        alertsContainer.removeAllViews();
        List<PriceAlert> alerts = alertRepository.load();

        if (alerts.isEmpty()) {
            TextView empty = text("No saved alerts yet.", 15, false, MUTED);
            empty.setPadding(0, dp(8), 0, 0);
            alertsContainer.addView(empty);
            return;
        }

        for (PriceAlert alert : alerts) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(10));
            card.setBackground(cardBackground(Color.WHITE, Color.rgb(211, 220, 224)));
            card.addView(text(alert.summary(), 14, false, INK));

            AlertEvaluator.Result evaluation = findEvaluation(evaluations, alert.key());
            if (evaluation != null) {
                TextView status = text(
                        evaluation.summary(),
                        14,
                        true,
                        evaluation.targetReached ? Color.rgb(21, 115, 67) : BRAND
                );
                status.setPadding(0, dp(8), 0, 0);
                card.addView(status);
            }

            Button delete = secondaryButton("Delete");
            delete.setOnClickListener(view -> {
                alertRepository.delete(alert.key());
                renderAlerts(Collections.emptyList());
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            deleteParams.gravity = Gravity.END;
            deleteParams.topMargin = dp(8);
            card.addView(delete, deleteParams);
            alertsContainer.addView(card, spacedParams(dp(3), dp(5)));
        }
    }

    private AlertEvaluator.Result findEvaluation(List<AlertEvaluator.Result> evaluations, String key) {
        if (evaluations == null) return null;
        for (AlertEvaluator.Result result : evaluations) {
            if (result.alert.key().equals(key)) return result;
        }
        return null;
    }

    private void confirmClearAlerts() {
        if (alertRepository.load().isEmpty()) {
            showMessage("There are no alerts to clear.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Clear all alerts?")
                .setMessage("This removes every locally saved price alert.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    alertRepository.clear();
                    renderAlerts(Collections.emptyList());
                })
                .show();
    }

    private SearchCriteria readCriteria() {
        return new SearchCriteria(
                origin.getText().toString(),
                destination.getText().toString(),
                departureDate.getText().toString(),
                returnDate.getText().toString(),
                String.valueOf(cabin.getSelectedItem()),
                roundTrip.isChecked(),
                parsePositiveInt(passengers, 0),
                String.valueOf(currency.getSelectedItem())
        );
    }

    private AutoCompleteTextView airportInput(String hint, String value) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setThreshold(1);
        styleInput(input);
        input.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                AirportCatalog.suggestions()
        ));
        return input;
    }

    private EditText standardInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setSingleLine(true);
        styleInput(input);
        return input;
    }

    private void styleInput(TextView input) {
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(cardBackground(Color.WHITE, Color.rgb(190, 204, 210)));
    }

    private EditText dateInput() {
        EditText input = standardInput("");
        input.setFocusable(false);
        input.setClickable(true);
        input.setInputType(InputType.TYPE_NULL);
        input.setOnClickListener(view -> showDatePicker(input));
        return input;
    }

    private void showDatePicker(EditText target) {
        LocalDate initial;
        try {
            initial = LocalDate.parse(target.getText().toString());
        } catch (RuntimeException ignored) {
            initial = LocalDate.now().plusDays(30);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (picker, year, month, day) -> target.setText(LocalDate.of(year, month + 1, day).toString()),
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        );
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000L);
        dialog.show();
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setPadding(dp(4), dp(4), dp(4), dp(4));
        return spinner;
    }

    private TextView sectionTitle(String value) {
        TextView heading = text(value, 21, true, INK);
        heading.setPadding(0, dp(24), 0, dp(8));
        return heading;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 13, true, MUTED);
        label.setPadding(0, dp(10), 0, dp(4));
        return label;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(cardBackground(BRAND, BRAND));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(BRAND);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(cardBackground(Color.WHITE, Color.rgb(160, 194, 205)));
        return button;
    }

    private GradientDrawable cardBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams spacedParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private LinearLayout.LayoutParams weightedButtonParams(float weight, int endMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), weight);
        params.rightMargin = endMargin;
        return params;
    }

    private int parsePositiveInt(EditText input, int fallback) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message == null ? "Something went wrong." : message, Toast.LENGTH_LONG).show();
    }
}
