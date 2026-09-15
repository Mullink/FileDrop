package com.liquorbee.updater;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class UpdateNotifications {
    private static final String CHANNEL = "pos_updates";
    private static final int NOTIFICATION_ID = 48102;
    private UpdateNotifications() { }

    public static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "LiquorBee POS updates", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Alerts when a newer LiquorBee POS build is available");
        manager.createNotificationChannel(channel);
    }

    public static boolean enabled(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    /** Called only for a fresh, successful check; a failed check must not claim the app is current. */
    @SuppressLint("MissingPermission") // enabled() checks POST_NOTIFICATIONS and the notification channel.
    public static synchronized void consider(Context context, ReleaseCheck check) {
        InstalledPos installed = InstalledPos.read(context);
        if (!installed.installed) { cancel(context); return; }
        if (check == null || check.failed()) return;
        long latest = check.published.code;
        if (installed.code >= latest) { cancel(context); return; }
        UpdateStore store = new UpdateStore(context);
        if (!UpdatePolicy.shouldNotify(store.monitoringEnabled(), enabled(context),
                true, installed.code, latest, store.notifiedCode())) return;
        createChannel(context);
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent review = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_update)
                .setColor(context.getColor(R.color.navy))
                .setContentTitle("LiquorBee POS update available")
                .setContentText("Build " + latest + " is available. Tap to review and download.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "A newer LiquorBee POS build is available. Tap to download it when you are ready to update."))
                .setContentIntent(review)
                .setAutoCancel(true)
                // The stored build suppresses duplicate posts. A newer build must alert
                // even when the previous build's notification is still visible.
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .addAction(new Notification.Action.Builder(null, "Review update", review).build())
                .build();
        try {
            context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
            store.markNotified(latest);
        } catch (SecurityException ignored) {
            // The user can revoke notification permission between the check and notify().
        }
    }

    public static void cancel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }
}
