package com.flightticketspricetracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public final class AlertCheckWorker extends Worker {
    public AlertCheckWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        ProviderConfig config = new SecureConfigStore(context).load();
        List<PriceAlert> alerts = new AlertRepository(context).load();
        if (!config.isConfigured() || alerts.isEmpty()) return Result.success();

        FlightService service = FlightServiceFactory.create(config);
        PriceHistoryRepository history = new PriceHistoryRepository(context);
        boolean retry = false;
        for (PriceAlert alert : alerts) {
            try {
                List<FareQuote> quotes = service.search(alert.criteria);
                if (quotes.isEmpty()) continue;
                FareQuote best = quotes.get(0);
                history.record(alert.key(), best);
                if (best.totalPrice.compareTo(alert.targetPrice) <= 0) {
                    NotificationHelper.notifyTargetReached(context, alert, best);
                }
            } catch (FlightServiceException exception) {
                retry = retry || exception.retryable;
            }
        }
        return retry ? Result.retry() : Result.success();
    }
}
