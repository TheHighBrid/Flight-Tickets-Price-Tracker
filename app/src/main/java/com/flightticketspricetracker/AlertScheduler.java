package com.flightticketspricetracker;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class AlertScheduler {
    private static final String WORK_NAME = "live-flight-price-checks";

    private AlertScheduler() {}

    public static void refresh(Context context) {
        ProviderConfig config = new SecureConfigStore(context).load();
        boolean hasAlerts = !new AlertRepository(context).load().isEmpty();
        WorkManager manager = WorkManager.getInstance(context);
        if (!config.isConfigured() || !hasAlerts) {
            manager.cancelUniqueWork(WORK_NAME);
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AlertCheckWorker.class,
                6,
                TimeUnit.HOURS
        ).setConstraints(constraints).build();
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }
}
