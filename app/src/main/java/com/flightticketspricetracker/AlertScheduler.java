package com.flightticketspricetracker;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class AlertScheduler {
    private static final int JOB_ID = 42021;
    private static final long CHECK_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L;

    private AlertScheduler() {}

    public static void refresh(Context context) {
        ProviderConfig config = new SecureConfigStore(context).load();
        boolean hasAlerts = !new AlertRepository(context).load().isEmpty();
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        if (!config.isConfigured() || !hasAlerts) {
            scheduler.cancel(JOB_ID);
            return;
        }

        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, AlertCheckJobService.class)
        )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(CHECK_INTERVAL_MILLIS)
                .build();
        scheduler.schedule(job);
    }
}
