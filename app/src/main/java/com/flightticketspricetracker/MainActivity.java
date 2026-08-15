package com.flightticketspricetracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SecureConfigStore configStore;
    private AlertRepository alertRepository;
    private PriceHistoryRepository historyRepository;
    private ProviderConfig providerConfig;

    private ScrollView scrollView;
    private FlightRadarView radarView;
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
    private LinearLayout returnDateBlock;
    private LinearLayout resultsContainer;
    private LinearLayout alertsContainer;
    private LinearLayout alertsSection;
    private TextView returnDateLabel;
    private TextView providerStatus;
    private Button searchButton;
    private ProgressBar progressBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
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

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(FlightTheme.NAVY);
        window.setNavigationBarColor(FlightTheme.NAVY);
        window.getDecorView().setSystemUiVisibility(0);
    }

    private View buildUi() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(FlightTheme.NAVY);

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(30));
        root.setBackground(FlightTheme.verticalGradient(FlightTheme.NAVY, Color.rgb(7, 20, 39), 0));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(buildHeroCard());
        root.addView(buildIntegrityBanner(), spacedParams(dp(12), dp(2)));
        root.addView(buildSearchCard(), spacedParams(dp(12), dp(2)));

        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.addView(emptyState(
                R.drawable.ic_radar,
                "Ready to track",
                "Search live provider inventory to compare verified fare offers."
        ));
        root.addView(resultsContainer, spacedParams(dp(12), dp(2)));

        alertsSection = buildAlertsSection();
        root.addView(alertsSection, spacedParams(dp(12), dp(8)));

        shell.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        shell.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76)
        ));
        return shell;
    }

    private View buildHeroCard() {
        LinearLayout hero = card(FlightTheme.SURFACE, FlightTheme.BORDER, 22);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout brandRow = horizontal();
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        logoParams.rightMargin = dp(12);
        brandRow.addView(logo, logoParams);

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("FLIGHT TRACKER", 19, true, FlightTheme.TEXT);
        brand.setLetterSpacing(0.08f);
        brandCopy.addView(brand);
        TextView strapline = text("LIVE  •  ACCURATE  •  GLOBAL", 10, true, FlightTheme.CYAN);
        strapline.setLetterSpacing(0.08f);
        strapline.setPadding(0, dp(3), 0, 0);
        brandCopy.addView(strapline);
        brandRow.addView(brandCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button settings = iconButton(R.drawable.ic_settings, "Provider");
        settings.setOnClickListener(view -> showProviderDialog());
        brandRow.addView(settings, new LinearLayout.LayoutParams(dp(98), dp(44)));
        hero.addView(brandRow);

        radarView = new FlightRadarView(this);
        radarView.setRouteLabels("YOW", "CMN");
        LinearLayout.LayoutParams radarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(190)
        );
        radarParams.topMargin = dp(14);
        hero.addView(radarView, radarParams);

        LinearLayout metrics = horizontal();
        metrics.setPadding(0, dp(12), 0, 0);
        metrics.addView(metric("REAL", "Provider fares", FlightTheme.CYAN), metricParams(dp(6)));
        metrics.addView(metric("SMART", "Price alerts", FlightTheme.AQUA), metricParams(dp(6)));
        metrics.addView(metric("ZERO", "Fake fallback", FlightTheme.SUCCESS), metricParams(0));
        hero.addView(metrics);
        return hero;
    }

    private View buildIntegrityBanner() {
        LinearLayout banner = horizontal();
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(13), dp(11), dp(13), dp(11));
        banner.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.10f),
                dp(16),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.40f)
        ));
        ImageView icon = iconView(R.drawable.ic_verified, FlightTheme.CYAN, 22);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.rightMargin = dp(10);
        banner.addView(icon, iconParams);
        TextView copy = text(
                "Verified provider data only. Errors stay honest, and generated fares never replace real results.",
                12,
                false,
                FlightTheme.ICE
        );
        copy.setLineSpacing(0, 1.08f);
        banner.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return banner;
    }

    private View buildSearchCard() {
        LinearLayout card = card(FlightTheme.SURFACE, FlightTheme.BORDER, 22);
        card.addView(sectionHeader(R.drawable.ic_search, "Track a route", "Search current flight offers"));

        LinearLayout providerRow = horizontal();
        providerRow.setGravity(Gravity.CENTER_VERTICAL);
        providerRow.setPadding(0, dp(14), 0, dp(4));
        providerStatus = text("", 12, true, FlightTheme.TEXT);
        providerStatus.setGravity(Gravity.CENTER_VERTICAL);
        providerStatus.setPadding(dp(12), dp(8), dp(12), dp(8));
        providerStatus.setMaxLines(2);
        providerRow.addView(providerStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button configure = secondaryButton("Configure");
        configure.setOnClickListener(view -> showProviderDialog());
        LinearLayout.LayoutParams configureParams = new LinearLayout.LayoutParams(dp(104), dp(44));
        configureParams.leftMargin = dp(8);
        providerRow.addView(configure, configureParams);
        card.addView(providerRow);

        card.addView(fieldLabel("ORIGIN"));
        origin = airportInput("Airport, city, or IATA code", "Ottawa (YOW)", R.drawable.ic_location);
        origin.setOnFocusChangeListener((view, focused) -> {
            if (!focused && radarView != null) radarView.setRouteLabels(origin.getText().toString(), destination == null ? "CMN" : destination.getText().toString());
        });
        card.addView(origin);

        LinearLayout swapRow = horizontal();
        swapRow.setGravity(Gravity.CENTER);
        Button swap = iconOnlyButton(R.drawable.ic_swap, "Swap route");
        swap.setOnClickListener(view -> swapRoute());
        swapRow.addView(swap, new LinearLayout.LayoutParams(dp(44), dp(38)));
        card.addView(swapRow, spacedParams(dp(3), dp(3)));

        card.addView(fieldLabel("DESTINATION"));
        destination = airportInput("Airport, city, or IATA code", "Casablanca (CMN)", R.drawable.ic_location);
        destination.setOnFocusChangeListener((view, focused) -> {
            if (!focused && radarView != null) radarView.setRouteLabels(origin.getText().toString(), destination.getText().toString());
        });
        card.addView(destination);

        LinearLayout tripModes = horizontal();
        tripModes.setPadding(0, dp(12), 0, dp(3));
        roundTrip = checkbox("Round trip", true);
        roundTrip.setOnCheckedChangeListener((button, checked) -> {
            if (returnDateBlock != null) returnDateBlock.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        tripModes.addView(roundTrip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nonStop = checkbox("Nonstop only", false);
        tripModes.addView(nonStop, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(tripModes);

        LinearLayout dates = horizontal();
        dates.addView(fieldBlock("DEPART", departureDate = dateInput(), R.drawable.ic_calendar), fieldPairParams(dp(6)));
        returnDate = dateInput();
        returnDateBlock = new LinearLayout(this);
        returnDateBlock.setOrientation(LinearLayout.VERTICAL);
        returnDateLabel = fieldLabel("RETURN");
        returnDateBlock.addView(returnDateLabel);
        applyInputIcon(returnDate, R.drawable.ic_calendar);
        returnDateBlock.addView(returnDate);
        dates.addView(returnDateBlock, fieldPairParams(0));
        card.addView(dates, spacedParams(dp(4), dp(3)));

        LinearLayout tripDetails = horizontal();
        passengers = standardInput("1");
        passengers.setInputType(InputType.TYPE_CLASS_NUMBER);
        applyInputIcon(passengers, R.drawable.ic_traveler);
        tripDetails.addView(fieldBlock("TRAVELLERS", passengers, 0), fieldThirdParams(dp(6)));
        cabin = spinner(new String[]{"Economy", "Premium Economy", "Business", "First"});
        tripDetails.addView(fieldBlock("CABIN", cabin, 0), fieldThirdParams(dp(6)));
        currency = spinner(new String[]{"CAD", "USD", "EUR", "MAD"});
        tripDetails.addView(fieldBlock("CURRENCY", currency, 0), fieldThirdParams(0));
        card.addView(tripDetails, spacedParams(dp(3), dp(6)));

        searchButton = primaryButton("Search live flights", R.drawable.ic_plane);
        searchButton.setOnClickListener(view -> runSearch());
        card.addView(searchButton, spacedParams(dp(10), dp(2)));

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(FlightTheme.CYAN));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(10);
        card.addView(progressBar, progressParams);
        return card;
    }

    private LinearLayout buildAlertsSection() {
        LinearLayout card = card(FlightTheme.SURFACE, FlightTheme.BORDER, 22);
        card.addView(sectionHeader(R.drawable.ic_bell, "Price alerts", "Watch a verified total fare"));

        card.addView(fieldLabel("TARGET TOTAL PRICE"));
        targetPrice = standardInput("700.00");
        targetPrice.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        applyInputIcon(targetPrice, R.drawable.ic_ticket);
        card.addView(targetPrice);

        LinearLayout actions = horizontal();
        Button saveAlert = primaryButton("Save alert", R.drawable.ic_bell);
        saveAlert.setOnClickListener(view -> saveAlert());
        actions.addView(saveAlert, weightedButtonParams(1f, dp(6)));
        Button checkNow = secondaryButton("Check now");
        checkNow.setOnClickListener(view -> checkAlertsNow());
        actions.addView(checkNow, weightedButtonParams(1f, 0));
        card.addView(actions, spacedParams(dp(10), dp(8)));

        Button clearAlerts = ghostButton("Clear alerts and price history");
        clearAlerts.setOnClickListener(view -> confirmClearAlerts());
        card.addView(clearAlerts, spacedParams(0, dp(8)));

        alertsContainer = new LinearLayout(this);
        alertsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(alertsContainer);
        return card;
    }

    private View buildBottomNavigation() {
        LinearLayout navigation = horizontal();
        navigation.setPadding(dp(8), dp(7), dp(8), dp(7));
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setBackground(FlightTheme.solid(FlightTheme.SURFACE, 0, dp(1), FlightTheme.BORDER));

        navigation.addView(navItem(R.drawable.ic_radar, "Track", true, () -> scrollView.smoothScrollTo(0, 0)), navParams());
        navigation.addView(navItem(R.drawable.ic_plane, "Flights", false, () -> scrollToView(resultsContainer)), navParams());
        navigation.addView(navItem(R.drawable.ic_airport, "Airports", false, () -> {
            scrollToView(origin);
            origin.requestFocus();
            origin.showDropDown();
        }), navParams());
        navigation.addView(navItem(R.drawable.ic_bell, "Alerts", false, () -> scrollToView(alertsSection)), navParams());
        navigation.addView(navItem(R.drawable.ic_more, "More", false, this::showProviderDialog), navParams());
        return navigation;
    }

    private View navItem(int iconRes, String label, boolean selected, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(5), dp(4), dp(4));
        if (selected) {
            item.setBackground(FlightTheme.solid(
                    FlightTheme.withAlpha(FlightTheme.ROYAL, 0.22f),
                    dp(14),
                    dp(1),
                    FlightTheme.withAlpha(FlightTheme.CYAN, 0.35f)
            ));
        }
        ImageView icon = iconView(iconRes, selected ? FlightTheme.CYAN : FlightTheme.MUTED, 23);
        item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
        TextView copy = text(label, 10, selected, selected ? FlightTheme.CYAN : FlightTheme.MUTED);
        copy.setPadding(0, dp(3), 0, 0);
        item.addView(copy);
        item.setOnClickListener(view -> action.run());
        return item;
    }

    private void setInitialDates() {
        LocalDate departure = LocalDate.now().plusDays(30);
        departureDate.setText(departure.toString());
        returnDate.setText(departure.plusDays(7).toString());
    }

    private void updateProviderStatus() {
        providerConfig = configStore.load();
        providerStatus.setText(providerConfig.statusLabel());
        int color;
        if (!providerConfig.isConfigured()) {
            color = FlightTheme.ERROR;
        } else {
            color = FlightTheme.SUCCESS;
        }
        providerStatus.setTextColor(color);
        providerStatus.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(color, 0.12f),
                dp(14),
                dp(1),
                FlightTheme.withAlpha(color, 0.55f)
        ));
    }

    private void showProviderDialog() {
        ProviderConfig current = configStore.load();
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        form.setBackgroundColor(FlightTheme.NAVY);

        form.addView(fieldLabel("CONNECTION MODE"));
        Spinner mode = spinner(new String[]{"SerpApi on this device", "Secure backend"});
        mode.setSelection(current.mode == ProviderConfig.Mode.SERPAPI_DIRECT ? 0 : 1);
        form.addView(mode);

        form.addView(fieldLabel("SECURE BACKEND URL"));
        EditText backendUrl = standardInput(current.backendUrl);
        backendUrl.setHint("https://your-flight-api.example.com");
        form.addView(backendUrl);

        form.addView(fieldLabel("BACKEND ACCESS TOKEN, OPTIONAL"));
        EditText backendToken = passwordInput(current.backendToken);
        form.addView(backendToken);

        form.addView(fieldLabel("SERPAPI API KEY"));
        EditText apiKey = passwordInput(current.apiKey);
        form.addView(apiKey);

        TextView note = text(
                "For private use, choose SerpApi on this device and paste one API key. The key is stored with Android Keystore. Secure backend mode remains available for distributed builds.",
                12,
                false,
                FlightTheme.MUTED
        );
        note.setLineSpacing(0, 1.1f);
        note.setPadding(0, dp(14), 0, 0);
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Configure real flight data")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(FlightTheme.CYAN);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(FlightTheme.MUTED);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(FlightTheme.ERROR);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                ProviderConfig config = new ProviderConfig(
                        mode.getSelectedItemPosition() == 0
                                ? ProviderConfig.Mode.SERPAPI_DIRECT
                                : ProviderConfig.Mode.BACKEND,
                        ProviderConfig.Environment.PRODUCTION,
                        apiKey.getText().toString(),
                        "",
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

        radarView.setRouteLabels(criteria.origin, criteria.destination);
        setBusy(true, "Searching provider inventory...");
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
        String heading = providerConfig.mode == ProviderConfig.Mode.SERPAPI_DIRECT
                && providerConfig.environment == ProviderConfig.Environment.TEST
                ? "Provider test results"
                : "Live provider results";
        resultsContainer.addView(sectionHeader(R.drawable.ic_plane, heading, criteria.route() + "  •  " + quotes.size() + " offers"));
        if (quotes.isEmpty()) {
            resultsContainer.addView(emptyState(
                    R.drawable.ic_search,
                    "No offers returned",
                    "The provider did not return a fare for this route and date combination."
            ), spacedParams(dp(8), 0));
            return;
        }
        for (FareQuote quote : quotes) {
            resultsContainer.addView(buildFareCard(quote), spacedParams(dp(8), 0));
        }
        scrollToView(resultsContainer);
    }

    private View buildFareCard(FareQuote quote) {
        LinearLayout offer = card(FlightTheme.SURFACE_2, FlightTheme.BORDER, 20);
        offer.setOnClickListener(view -> {
            targetPrice.setText(quote.totalPrice.toPlainString());
            showMessage("Target set to " + quote.priceLabel() + ".");
        });

        LinearLayout header = horizontal();
        header.setGravity(Gravity.TOP);
        LinearLayout carrier = new LinearLayout(this);
        carrier.setOrientation(LinearLayout.VERTICAL);
        TextView airline = text(quote.airlineNames, 16, true, FlightTheme.TEXT);
        airline.setMaxLines(2);
        carrier.addView(airline);
        TextView flight = text(quote.flightNumbers, 12, false, FlightTheme.MUTED);
        flight.setPadding(0, dp(3), 0, 0);
        carrier.addView(flight);
        header.addView(carrier, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView price = text(quote.priceLabel(), 16, true, FlightTheme.AQUA);
        price.setGravity(Gravity.CENTER);
        price.setPadding(dp(10), dp(7), dp(10), dp(7));
        price.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.12f),
                dp(13),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.45f)
        ));
        header.addView(price);
        offer.addView(header);

        TextView route = text(quote.route, 22, true, FlightTheme.TEXT);
        route.setLetterSpacing(0.04f);
        route.setPadding(0, dp(12), 0, dp(6));
        offer.addView(route);

        LinearLayout chips = horizontal();
        chips.addView(statusChip(quote.stopsLabel(), quote.stops == 0 ? FlightTheme.SUCCESS : FlightTheme.WARNING));
        chips.addView(statusChip(quote.durationLabel(), FlightTheme.CYAN), chipParams());
        offer.addView(chips);

        offer.addView(detailRow(R.drawable.ic_plane, "Outbound", quote.outbound), spacedParams(dp(10), 0));
        if (!quote.inbound.isEmpty()) {
            offer.addView(detailRow(R.drawable.ic_plane, "Return", quote.inbound), spacedParams(dp(7), 0));
        }
        offer.addView(detailRow(R.drawable.ic_ticket, "Baggage", quote.baggage), spacedParams(dp(7), 0));

        View divider = new View(this);
        divider.setBackgroundColor(FlightTheme.withAlpha(FlightTheme.ICE, 0.12f));
        offer.addView(divider, spacedFixedParams(dp(1), dp(12), dp(10)));

        TextView verified = text(
                quote.provider + "  •  " + quote.environment + "  •  " + quote.verifiedLabel(),
                11,
                false,
                FlightTheme.MUTED
        );
        verified.setLineSpacing(0, 1.06f);
        offer.addView(verified);
        TextView action = text("Tap this offer to use its total as your alert target", 11, true, FlightTheme.CYAN);
        action.setPadding(0, dp(8), 0, 0);
        offer.addView(action);
        return offer;
    }

    private void renderError(String message) {
        resultsContainer.removeAllViews();
        resultsContainer.addView(sectionHeader(R.drawable.ic_alert, "Search unavailable", "The provider returned an error"));
        TextView error = text(message == null ? "Flight search failed." : message, 14, true, FlightTheme.ERROR);
        error.setLineSpacing(0, 1.12f);
        error.setPadding(dp(14), dp(13), dp(14), dp(13));
        error.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(FlightTheme.ERROR, 0.10f),
                dp(16),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.ERROR, 0.55f)
        ));
        resultsContainer.addView(error, spacedParams(dp(8), 0));
        scrollToView(resultsContainer);
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
            showMessage("Live price alert saved. Android will check it about every six hours when permitted.");
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
        setBusy(true, "Checking saved alerts against provider inventory...");
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
            alertsContainer.addView(emptyState(
                    R.drawable.ic_bell,
                    "No saved alerts",
                    "Choose a target price and save it to begin monitoring."
            ));
            return;
        }
        for (PriceAlert alert : alerts) {
            alertsContainer.addView(buildAlertCard(alert), spacedParams(dp(8), 0));
        }
    }

    private View buildAlertCard(PriceAlert alert) {
        LinearLayout alertCard = card(FlightTheme.SURFACE_2, FlightTheme.BORDER, 18);
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView route = text(alert.criteria.route(), 18, true, FlightTheme.TEXT);
        copy.addView(route);
        String dates = alert.criteria.departureDate + (alert.criteria.roundTrip ? "  →  " + alert.criteria.returnDate : "");
        TextView date = text(dates + "  •  " + alert.criteria.cabin, 12, false, FlightTheme.MUTED);
        date.setPadding(0, dp(3), 0, 0);
        copy.addView(date);
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView target = text(alert.criteria.currency + " $" + alert.targetPrice.toPlainString(), 14, true, FlightTheme.AQUA);
        target.setPadding(dp(9), dp(6), dp(9), dp(6));
        target.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.11f),
                dp(12),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.42f)
        ));
        header.addView(target);
        alertCard.addView(header);

        PriceHistoryRepository.Entry latest = historyRepository.latest(alert.key());
        if (latest == null) {
            alertCard.addView(statusChip("Waiting for first provider check", FlightTheme.WARNING), spacedParams(dp(10), 0));
        } else {
            String checked = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(latest.checkedAt));
            boolean reached = latest.price.compareTo(alert.targetPrice) <= 0;
            LinearLayout latestRow = horizontal();
            latestRow.setGravity(Gravity.CENTER_VERTICAL);
            latestRow.setPadding(0, dp(10), 0, 0);
            latestRow.addView(statusChip(reached ? "Target reached" : "Watching", reached ? FlightTheme.SUCCESS : FlightTheme.CYAN));
            TextView latestPrice = text(
                    "Latest " + latest.currency + " $" + latest.price.toPlainString() + "  •  " + checked,
                    12,
                    false,
                    FlightTheme.MUTED
            );
            LinearLayout.LayoutParams latestParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            latestParams.leftMargin = dp(8);
            latestRow.addView(latestPrice, latestParams);
            alertCard.addView(latestRow);
        }

        LinearLayout footer = horizontal();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(10), 0, 0);
        String options = alert.criteria.passengers + (alert.criteria.passengers == 1 ? " traveller" : " travellers")
                + (alert.criteria.nonStop ? "  •  nonstop" : "");
        footer.addView(text(options, 11, false, FlightTheme.MUTED), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button delete = dangerButton("Delete");
        delete.setOnClickListener(view -> {
            alertRepository.delete(alert.key());
            historyRepository.delete(alert.key());
            AlertScheduler.refresh(this);
            renderAlerts();
        });
        footer.addView(delete, new LinearLayout.LayoutParams(dp(84), dp(40)));
        alertCard.addView(footer);
        return alertCard;
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
        searchButton.setAlpha(busy ? 0.58f : 1f);
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy && message != null) {
            resultsContainer.removeAllViews();
            LinearLayout loading = horizontal();
            loading.setGravity(Gravity.CENTER_VERTICAL);
            loading.setPadding(dp(14), dp(14), dp(14), dp(14));
            loading.setBackground(FlightTheme.solid(FlightTheme.SURFACE, dp(18), dp(1), FlightTheme.BORDER));
            ImageView radar = iconView(R.drawable.ic_radar, FlightTheme.CYAN, 24);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            iconParams.rightMargin = dp(10);
            loading.addView(radar, iconParams);
            loading.addView(text(message, 14, true, FlightTheme.CYAN));
            resultsContainer.addView(loading);
        }
    }

    private LinearLayout card(int fill, int stroke, int radiusDp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(FlightTheme.solid(fill, dp(radiusDp), dp(1), stroke));
        card.setElevation(dp(3));
        return card;
    }

    private AutoCompleteTextView airportInput(String hint, String value, int iconRes) {
        AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setThreshold(1);
        styleInput(input);
        applyInputIcon(input, iconRes);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, AirportCatalog.suggestions()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return styleAdapterText(super.getView(position, convertView, parent), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return styleAdapterText(super.getDropDownView(position, convertView, parent), true);
            }
        };
        input.setAdapter(adapter);
        input.setDropDownBackgroundDrawable(FlightTheme.solid(FlightTheme.SURFACE_2, dp(12), dp(1), FlightTheme.BORDER));
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
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return styleAdapterText(super.getView(position, convertView, parent), false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return styleAdapterText(super.getDropDownView(position, convertView, parent), true);
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(9), 0, dp(6), 0);
        spinner.setBackground(FlightTheme.solid(FlightTheme.NAVY_2, dp(14), dp(1), FlightTheme.BORDER));
        spinner.setMinimumHeight(dp(52));
        return spinner;
    }

    private View styleAdapterText(View view, boolean dropdown) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(FlightTheme.TEXT);
            textView.setTextSize(14);
            textView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            textView.setPadding(dp(12), dp(11), dp(12), dp(11));
            if (dropdown) textView.setBackgroundColor(FlightTheme.SURFACE_2);
        }
        return view;
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(FlightTheme.TEXT);
        box.setTextSize(14);
        box.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.setChecked(checked);
        box.setButtonTintList(FlightTheme.checkTint(FlightTheme.CYAN, FlightTheme.MUTED));
        box.setPadding(0, dp(5), 0, dp(4));
        return box;
    }

    private void styleInput(TextView input) {
        input.setTextColor(FlightTheme.TEXT);
        input.setHintTextColor(FlightTheme.withAlpha(FlightTheme.MUTED, 0.72f));
        input.setTextSize(14);
        input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setMinHeight(dp(52));
        input.setBackground(FlightTheme.solid(FlightTheme.NAVY_2, dp(14), dp(1), FlightTheme.BORDER));
    }

    private void applyInputIcon(TextView input, int iconRes) {
        if (iconRes == 0) return;
        input.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        input.setCompoundDrawablePadding(dp(9));
        input.setCompoundDrawableTintList(ColorStateList.valueOf(FlightTheme.CYAN));
    }

    private LinearLayout fieldBlock(String label, View field, int iconRes) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(fieldLabel(label));
        if (iconRes != 0 && field instanceof TextView) applyInputIcon((TextView) field, iconRes);
        block.addView(field);
        return block;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 10, true, FlightTheme.MUTED);
        label.setLetterSpacing(0.08f);
        label.setPadding(0, dp(10), 0, dp(5));
        return label;
    }

    private View sectionHeader(int iconRes, String title, String subtitle) {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = iconView(iconRes, FlightTheme.CYAN, 26);
        icon.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(FlightTheme.ROYAL, 0.22f),
                dp(13),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.32f)
        ));
        icon.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconParams.rightMargin = dp(11);
        header.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 19, true, FlightTheme.TEXT));
        TextView sub = text(subtitle, 11, false, FlightTheme.MUTED);
        sub.setPadding(0, dp(2), 0, 0);
        copy.addView(sub);
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private View emptyState(int iconRes, String title, String body) {
        LinearLayout state = new LinearLayout(this);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(18), dp(22), dp(18), dp(22));
        state.setBackground(FlightTheme.solid(FlightTheme.SURFACE, dp(20), dp(1), FlightTheme.BORDER));
        ImageView icon = iconView(iconRes, FlightTheme.CYAN, 34);
        state.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView heading = text(title, 16, true, FlightTheme.TEXT);
        heading.setPadding(0, dp(9), 0, dp(4));
        state.addView(heading);
        TextView copy = text(body, 12, false, FlightTheme.MUTED);
        copy.setGravity(Gravity.CENTER);
        copy.setLineSpacing(0, 1.1f);
        state.addView(copy);
        return state;
    }

    private View detailRow(int iconRes, String label, String value) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        ImageView icon = iconView(iconRes, FlightTheme.CYAN, 18);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconParams.topMargin = dp(2);
        iconParams.rightMargin = dp(9);
        row.addView(icon, iconParams);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label.toUpperCase(Locale.CANADA), 9, true, FlightTheme.MUTED));
        TextView detail = text(value, 12, false, FlightTheme.ICE);
        detail.setPadding(0, dp(2), 0, 0);
        detail.setLineSpacing(0, 1.08f);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private TextView statusChip(String value, int color) {
        TextView chip = text(value, 11, true, color);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(color, 0.11f),
                dp(13),
                dp(1),
                FlightTheme.withAlpha(color, 0.55f)
        ));
        return chip;
    }

    private View metric(String value, String label, int color) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setPadding(dp(7), dp(9), dp(7), dp(9));
        metric.setBackground(FlightTheme.solid(
                FlightTheme.withAlpha(color, 0.08f),
                dp(14),
                dp(1),
                FlightTheme.withAlpha(color, 0.28f)
        ));
        TextView number = text(value, 12, true, color);
        number.setLetterSpacing(0.06f);
        metric.addView(number);
        TextView copy = text(label, 9, false, FlightTheme.MUTED);
        copy.setPadding(0, dp(2), 0, 0);
        metric.addView(copy);
        return metric;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    private Button primaryButton(String label, int iconRes) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(FlightTheme.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(54));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(FlightTheme.horizontalGradient(FlightTheme.ROYAL, FlightTheme.CYAN, dp(16)));
        if (iconRes != 0) {
            button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(9));
            button.setCompoundDrawableTintList(ColorStateList.valueOf(FlightTheme.WHITE));
        }
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(FlightTheme.CYAN);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(44));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(FlightTheme.pressable(
                FlightTheme.NAVY_2,
                FlightTheme.DEEP_BLUE,
                dp(14),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.55f)
        ));
        return button;
    }

    private Button ghostButton(String label) {
        Button button = secondaryButton(label);
        button.setTextColor(FlightTheme.MUTED);
        button.setBackground(FlightTheme.pressable(
                Color.TRANSPARENT,
                FlightTheme.withAlpha(FlightTheme.ICE, 0.06f),
                dp(14),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.MUTED, 0.35f)
        ));
        return button;
    }

    private Button dangerButton(String label) {
        Button button = secondaryButton(label);
        button.setTextColor(FlightTheme.ERROR);
        button.setBackground(FlightTheme.pressable(
                FlightTheme.withAlpha(FlightTheme.ERROR, 0.08f),
                FlightTheme.withAlpha(FlightTheme.ERROR, 0.16f),
                dp(12),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.ERROR, 0.55f)
        ));
        return button;
    }

    private Button iconButton(int iconRes, String label) {
        Button button = secondaryButton(label);
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(6));
        button.setCompoundDrawableTintList(ColorStateList.valueOf(FlightTheme.CYAN));
        return button;
    }

    private Button iconOnlyButton(int iconRes, String contentDescription) {
        Button button = new Button(this);
        button.setText("");
        button.setContentDescription(contentDescription);
        button.setBackground(FlightTheme.pressable(
                FlightTheme.NAVY_2,
                FlightTheme.DEEP_BLUE,
                dp(19),
                dp(1),
                FlightTheme.withAlpha(FlightTheme.CYAN, 0.45f)
        ));
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawableTintList(ColorStateList.valueOf(FlightTheme.CYAN));
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private ImageView iconView(int iconRes, int tint, int sizeDp) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setAdjustViewBounds(true);
        icon.setMinimumWidth(dp(sizeDp));
        icon.setMinimumHeight(dp(sizeDp));
        return icon;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void swapRoute() {
        String from = origin.getText().toString();
        origin.setText(destination.getText().toString());
        destination.setText(from);
        radarView.setRouteLabels(origin.getText().toString(), destination.getText().toString());
    }

    private void scrollToView(View target) {
        if (target == null || scrollView == null) return;
        scrollView.post(() -> scrollView.smoothScrollTo(0, Math.max(0, target.getTop() - dp(12))));
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

    private LinearLayout.LayoutParams spacedFixedParams(int height, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
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

    private LinearLayout.LayoutParams fieldPairParams(int endMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = endMargin;
        return params;
    }

    private LinearLayout.LayoutParams fieldThirdParams(int endMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = endMargin;
        return params;
    }

    private LinearLayout.LayoutParams metricParams(int endMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = endMargin;
        return params;
    }

    private LinearLayout.LayoutParams navParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(6);
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
