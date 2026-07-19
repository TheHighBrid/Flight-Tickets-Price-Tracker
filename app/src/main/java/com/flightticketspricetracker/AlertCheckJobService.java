package com.flightticketspricetracker;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlertCheckJobService extends JobService {
    private ExecutorService executor;

    @Override
    public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean retry = checkAlerts();
            jobFinished(params, retry);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (executor != null) executor.shutdownNow();
        return true;
    }

    private boolean checkAlerts() {
        ProviderConfig config = new SecureConfigStore(this).load();
        List<PriceAlert> alerts = new AlertRepository(this).load();
        if (!config.isConfigured() || alerts.isEmpty()) return false;

        FlightService service = FlightServiceFactory.create(config);
        PriceHistoryRepository history = new PriceHistoryRepository(this);
        boolean retry = false;
        for (PriceAlert alert : alerts) {
            if (Thread.currentThread().isInterrupted()) return true;
            try {
                List<FareQuote> quotes = service.search(alert.criteria);
                if (quotes.isEmpty()) continue;
                FareQuote best = quotes.get(0);
                history.record(alert.key(), best);
                if (best.totalPrice.compareTo(alert.targetPrice) <= 0) {
                    NotificationHelper.notifyTargetReached(this, alert, best);
                }
            } catch (FlightServiceException exception) {
                retry = retry || exception.retryable;
            }
        }
        return retry;
    }
}
