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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BRAND = Color.rgb(19, 91, 124);
    private static final int INK = Color.rgb(20, 30, 34);
    private static final int MUTED = Color.rgb(79, 96, 103);
    private static final int SURFACE = Color.rgb(239, 247, 250);
    private static final int SUCCESS = Color.rgb(18, 111, 61);
    private static final int WARNING = Color.rgb(151, 101, 0);
    private static final int ERROR = Color.rgb(166, 42, 42);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SecureConfigStore configStore;
    private AlertRepository alertRepository;
    private PriceHistoryRepository historyRepository;
    private ProviderConfig providerConfig;

    private AutoCompleteTextView origin;
    private AutoCompleteTextView destination;
    private EditText departureDate;
    private EditText returnDate;
    private EditText passengers;
    private EditText targetPrice;
    private Spinner cabin;
    private Spinner currency;
    private CheckBox roundTrip;
    private CheckBox nonStop;
    private LinearLayout resultsContainer;
    private LinearLayout alertsContainer;
    private TextView returnDateLabel;
    private TextView providerStatus;
    private Button searchButton;
    private ProgressBar progressBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new SecureConfigStore(this);
        alertRepository = new AlertRepository(this);
        historyRepository = new PriceHistoryRepository(this);
        providerConfig = configStore.load();
        setContentView(buildUi());
        setInitialDates();
        updateProviderStatus();
        renderAlerts();
        AlertScheduler.refresh(this);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
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

        root.addView(text("Live Flight Price Tracker", 28, true, INK));
        TextView subtitle = text("Search provider inventory and monitor real fare changes.", 15, false, MUTED);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        LinearLayout providerCard = card(Color.WHITE, Color.rgb(188, 205, 212));
        providerStatus = text("", 14, true, INK);
        providerCard.addView(providerStatus);
        Button configure = secondaryButton("Configure live provider");
        configure.setOnClickListener(view -> showProviderDialog());
        providerCard.addView(configure, spacedParams(dp(8), 0));
        root.addView(providerCard, spacedParams(0, dp(10)));

        TextView integrity = text(
                "No simulated fares. No invented airlines. Searches either return provider data or a clear error.",
                13,
                true,
                BRAND
        );
        integrity.setPadding(dp(12), dp(10), dp(12), dp(10));
        integrity.setBackground(cardBackground(SURFACE, Color.rgb(191, 220, 231)));
        root.addView(integrity, spacedParams(0, dp(10)));

        root.addView(sectionTitle("Search live flights"));
        root.addView(fieldLabel("Origin"));
        origin = airportInput("Origin airport or IATA code", "Ottawa (YOW)");
        root.addView(origin);

        root.addView(fieldLabel("Destination"));
        destination = airportInput("Destination airport or IATA code", "Casablanca (CMN)");
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
        passengers.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(passengers);

        root.addView(fieldLabel("Cabin"));
        cabin = spinner(new String[]{"Economy", "Premium Economy", "Business", "First"});
        root.addView(cabin);

        root.addView(fieldLabel("Currency"));
        currency = spinner(new String[]{"CAD", "USD", "EUR", "MAD"});
        root.addView(currency);

        roundTrip = checkbox("Round trip", true);
        roundTrip.setOnCheckedChangeListener((button, checked) -> {
            returnDateLabel.setVisibility(checked ? View.VISIBLE : View.GONE);
            returnDate.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        root.addView(roundTrip);

        nonStop = checkbox("Nonstop only", false);
        root.addView(nonStop);

        searchButton = primaryButton("Search provider inventory");
        searchButton.setOnClickListener(view -> runSearch());
        root.addView(searchButton, spacedParams(dp(5), dp(8)));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(8);
        root.addView(progressBar, progressParams);

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.addView(text("Run a search to retrieve provider fares.", 15, false, MUTED));
        root.addView(resultsContainer);

        root.addView(sectionTitle("Price alerts"));
        root.addView(fieldLabel("Target total price"));
        targetPrice = standardInput("700.00");
        targetPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        root.addView(targetPrice);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button saveAlert = primaryButton("Save alert");
        saveAlert.setOnClickListener(view -> saveAlert());
        actions.addView(saveAlert, weightedButtonParams(1f, dp(6)));
        Button checkNow = secondaryButton("Check now");
        checkNow.setOnClickListener(view -> checkAlertsNow());
        actions.addView(checkNow, weightedButtonParams(1f, 0));
        root.addView(actions, spacedParams(dp(5), dp(10)));

        Button clearAlerts = secondaryButton("Clear alerts and price history");
        clearAlerts.setOnClickListener(view -> confirmClearAlerts());
        root.addView(clearAlerts, spacedParams(0, dp(6)));

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

    private void updateProviderStatus() {
        providerConfig = configStore.load();
        providerStatus.setText(providerConfig.statusLabel());
        if (!providerConfig.isConfigured()) {
            providerStatus.setTextColor(ERROR);
        } else if (providerConfig.mode == ProviderConfig.Mode.AMADEUS_DIRECT
                && providerConfig.environment == ProviderConfig.Environment.TEST) {
            providerStatus.setTextColor(WARNING);
        } else {
            providerStatus.setTextColor(SUCCESS);
        }
    }

    private void showProviderDialog() {
        ProviderConfig current = configStore.load();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), 0);

        form.addView(fieldLabel("Connection mode"));
        Spinner mode = spinner(new String[]{"Secure backend", "Amadeus API on this device"});
        mode.setSelection(current.mode == ProviderConfig.Mode.BACKEND ? 0 : 1);
        form.addView(mode);

        form.addView(fieldLabel("Amadeus environment"));
        Spinner environment = spinner(new String[]{"Production live inventory", "Test environment"});
        environment.setSelection(current.environment == ProviderConfig.Environment.PRODUCTION ? 0 : 1);
        form.addView(environment);

        form.addView(fieldLabel("Secure backend URL"));
        EditText backendUrl = standardInput(current.backendUrl);
        backendUrl.setHint("https://your-flight-api.example.com");
        form.addView(backendUrl);

        form.addView(fieldLabel("Backend access token, optional"));
        EditText backendToken = passwordInput(current.backendToken);
        form.addView(backendToken);

        form.addView(fieldLabel("Amadeus API key"));
        EditText apiKey = passwordInput(current.apiKey);
        form.addView(apiKey);

        form.addView(fieldLabel("Amadeus API secret"));
        EditText apiSecret = passwordInput(current.apiSecret);
        form.addView(apiSecret);

        TextView note = text(
                "Secure backend mode is recommended. Device mode is for private use and stores credentials encrypted with Android Keystore. Test environment data is limited and must not be treated as live pricing.",
                13,
                false,
                MUTED
        );
        note.setPadding(0, dp(12), 0, 0);
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configure real flight data")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                ProviderConfig config = new ProviderConfig(
                        mode.getSelectedItemPosition() == 0
                                ? ProviderConfig.Mode.BACKEND
                                : ProviderConfig.Mode.AMADEUS_DIRECT,
                        environment.getSelectedItemPosition() == 0
                                ? ProviderConfig.Environment.PRODUCTION
                                : ProviderConfig.Environment.TEST,
                        apiKey.getText().toString(),
                        apiSecret.getText().toString(),
                        backendUrl.getText().toString(),
                        backendToken.getText().toString()
                );
                String error = config.validationError();
                if (error != null) {
                    showMessage(error);
                    return;
                }
                configStore.save(config);
                updateProviderStatus();
                AlertScheduler.refresh(this);
                dialog.dismiss();
                showMessage("Provider configuration saved securely.");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                configStore.clear();
                updateProviderStatus();
                AlertScheduler.refresh(this);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void runSearch() {
        providerConfig = configStore.load();
        if (!providerConfig.isConfigured()) {
            showMessage("Configure a live provider first.");
            showProviderDialog();
            return;
        }
        SearchCriteria criteria = readCriteria();
        String error = criteria.firstValidationError();
        if (error != null) {
            showMessage(error);
            return;
        }

        setBusy(true, "Searching provider inventory…");
        executor.submit(() -> {
            try {
                List<FareQuote> quotes = FlightServiceFactory.create(providerConfig).search(criteria);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    renderQuotes(criteria, quotes);
                });
            } catch (FlightServiceException exception) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    renderError(exception.getMessage());
                });
            }
        });
    }

    private void renderQuotes(SearchCriteria criteria, List<FareQuote> quotes) {
        resultsContainer.removeAllViews();
        String heading = providerConfig.mode == ProviderConfig.Mode.AMADEUS_DIRECT
                && providerConfig.environment == ProviderConfig.Environment.TEST
                ? "Provider test results"
                : "Live provider results";
        resultsContainer.addView(sectionTitle(heading));
        resultsContainer.addView(text(criteria.route() + " • " + quotes.size() + " offers", 14, false, MUTED));
        if (quotes.isEmpty()) {
            resultsContainer.addView(text("The provider returned no offers for this search.", 15, false, MUTED));
            return;
        }
        for (FareQuote quote : quotes) {
            TextView row = text(quote.summary(), 15, false, INK);
            row.setLineSpacing(0, 1.08f);
            row.setPadding(dp(15), dp(13), dp(15), dp(13));
            row.setBackground(cardBackground(SURFACE, Color.rgb(194, 219, 229)));
            row.setOnClickListener(view -> {
                targetPrice.setText(quote.totalPrice.toPlainString());
                showMessage("Target set to " + quote.priceLabel() + ".");
            });
            resultsContainer.addView(row, spacedParams(dp(5), dp(5)));
        }
    }

    private void renderError(String message) {
        resultsContainer.removeAllViews();
        TextView error = text(message == null ? "Flight search failed." : message, 15, true, ERROR);
        error.setPadding(dp(13), dp(12), dp(13), dp(12));
        error.setBackground(cardBackground(Color.rgb(255, 242, 242), Color.rgb(230, 180, 180)));
        resultsContainer.addView(error, spacedParams(dp(8), 0));
    }

    private void saveAlert() {
        providerConfig = configStore.load();
        if (!providerConfig.isConfigured()) {
            showMessage("Configure a live provider before saving alerts.");
            return;
        }
        SearchCriteria criteria = readCriteria();
        String error = criteria.firstValidationError();
        if (error != null) {
            showMessage(error);
            return;
        }
        BigDecimal target;
        try {
            target = new BigDecimal(targetPrice.getText().toString().trim());
        } catch (RuntimeException exception) {
            targetPrice.setError("Enter a valid target amount.");
            return;
        }
        try {
            alertRepository.save(new PriceAlert(criteria, target));
            NotificationHelper.requestPermission(this);
            AlertScheduler.refresh(this);
            renderAlerts();
            showMessage("Live price alert saved. It will be checked about every six hours when Android permits.");
        } catch (IllegalArgumentException exception) {
            showMessage(exception.getMessage());
        }
    }

    private void checkAlertsNow() {
        providerConfig = configStore.load();
        List<PriceAlert> alerts = alertRepository.load();
        if (!providerConfig.isConfigured()) {
            showMessage("Configure a live provider first.");
            return;
        }
        if (alerts.isEmpty()) {
            showMessage("Save an alert first.");
            return;
        }
        setBusy(true, "Checking saved alerts against provider inventory…");
        executor.submit(() -> {
            FlightService service = FlightServiceFactory.create(providerConfig);
            int checked = 0;
            int reached = 0;
            String lastError = null;
            for (PriceAlert alert : alerts) {
                try {
                    List<FareQuote> quotes = service.search(alert.criteria);
                    if (quotes.isEmpty()) continue;
                    FareQuote best = quotes.get(0);
                    historyRepository.record(alert.key(), best);
                    checked++;
                    if (best.totalPrice.compareTo(alert.targetPrice) <= 0) {
                        reached++;
                        NotificationHelper.notifyTargetReached(this, alert, best);
                    }
                } catch (FlightServiceException exception) {
                    lastError = exception.getMessage();
                }
            }
            int finalChecked = checked;
            int finalReached = reached;
            String finalError = lastError;
            runOnUiThread(() -> {
                setBusy(false, null);
                renderAlerts();
                if (finalChecked == 0 && finalError != null) {
                    showMessage(finalError);
                } else {
                    showMessage("Checked " + finalChecked + " alert" + (finalChecked == 1 ? "" : "s")
                            + ". " + finalReached + " target" + (finalReached == 1 ? "" : "s") + " reached.");
                }
            });
        });
    }

    private void renderAlerts() {
        if (alertsContainer == null) return;
        alertsContainer.removeAllViews();
        List<PriceAlert> alerts = alertRepository.load();
        if (alerts.isEmpty()) {
            alertsContainer.addView(text("No live price alerts saved.", 15, false, MUTED));
            return;
        }
        for (PriceAlert alert : alerts) {
            LinearLayout card = card(Color.WHITE, Color.rgb(206, 218, 223));
            card.addView(text(alert.summary(), 14, false, INK));
            PriceHistoryRepository.Entry latest = historyRepository.latest(alert.key());
            if (latest != null) {
                String checked = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(latest.checkedAt));
                TextView status = text("Last provider price: " + latest.currency + " $" + latest.price.toPlainString()
                        + " • " + checked, 14, true,
                        latest.price.compareTo(alert.targetPrice) <= 0 ? SUCCESS : BRAND);
                status.setPadding(0, dp(8), 0, 0);
                card.addView(status);
            }
            Button delete = secondaryButton("Delete");
            delete.setOnClickListener(view -> {
                alertRepository.delete(alert.key());
                historyRepository.delete(alert.key());
                AlertScheduler.refresh(this);
                renderAlerts();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.END;
            params.topMargin = dp(8);
            card.addView(delete, params);
            alertsContainer.addView(card, spacedParams(dp(4), dp(4)));
        }
    }

    private void confirmClearAlerts() {
        if (alertRepository.load().isEmpty()) {
            showMessage("There are no alerts to clear.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Clear all alerts?")
                .setMessage("This removes every alert and its recorded provider prices.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    alertRepository.clear();
                    historyRepository.clear();
                    AlertScheduler.refresh(this);
                    renderAlerts();
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
                nonStop.isChecked(),
                parsePositiveInt(passengers, 0),
                String.valueOf(currency.getSelectedItem())
        );
    }

    private void setBusy(boolean busy, String message) {
        searchButton.setEnabled(!busy);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy && message != null) {
            resultsContainer.removeAllViews();
            resultsContainer.addView(text(message, 15, true, BRAND));
        }
    }

    private LinearLayout card(int fill, int stroke) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(cardBackground(fill, stroke));
        return card;
    }

    private AutoCompleteTextView airportInput(String hint, String value) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setThreshold(1);
        styleInput(input);
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, AirportCatalog.suggestions()));
        return input;
    }

    private EditText standardInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setSingleLine(true);
        styleInput(input);
        return input;
    }

    private EditText passwordInput(String value) {
        EditText input = standardInput(value);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
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

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(INK);
        box.setTextSize(16);
        box.setChecked(checked);
        box.setPadding(0, dp(6), 0, dp(4));
        return box;
    }

    private void styleInput(TextView input) {
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(cardBackground(Color.WHITE, Color.rgb(190, 204, 210)));
    }

    private TextView sectionTitle(String value) {
        TextView heading = text(value, 21, true, INK);
        heading.setPadding(0, dp(22), 0, dp(7));
        return heading;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 13, true, MUTED);
        label.setPadding(0, dp(9), 0, dp(4));
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
