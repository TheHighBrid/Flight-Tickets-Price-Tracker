package com.flightticketspricetracker;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "price_alerts";

    private NotificationHelper() {}

    public static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9001);
        }
    }

    public static void notifyTargetReached(Context context, PriceAlert alert, FareQuote quote) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Flight price alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications when a real provider fare reaches a saved target.");
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                alert.key().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("Flight target reached: " + alert.criteria.route())
                .setContentText(quote.priceLabel() + " with " + quote.airlineNames)
                .setStyle(new Notification.BigTextStyle().bigText(quote.summary()))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        manager.notify(alert.key().hashCode(), builder.build());
    }
}
