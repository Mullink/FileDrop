package com.liquorbee.updater;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A short, visible check started by the operator's exact daily alarm. */
public final class ScheduledCheckService extends Service {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Future<?> work;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean stopped;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) { stopSelf(); return; }
        String channel = "scheduled_check";
        manager.createNotificationChannel(new NotificationChannel(channel,
                getString(R.string.scheduled_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent review = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_update).setContentTitle(getString(R.string.scheduled_check_title))
                .setContentText(getString(R.string.checking_message)).setContentIntent(review)
                .setOngoing(true).setOnlyAlertOnce(true).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(48105, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(48105, notification);
        PowerManager power = getSystemService(PowerManager.class);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiquorBeeUpdater:dailyCheck");
            wakeLock.acquire(90000L);
        }
        handler.postDelayed(() -> {
            stopped = true;
            new UpdateStore(this).save(new ReleaseCheck(null,
                    "The scheduled check timed out. Check your connection and try Check now.", System.currentTimeMillis()));
            stopSelf();
        }, 90000L);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        UpdateStore store = new UpdateStore(this);
        long revision = store.scheduleRevision();
        long expected = intent == null ? 0 : intent.getLongExtra(UpdateScheduler.EXTRA_SCHEDULED_AT, 0);
        if (!store.claimScheduledCheck(expected, System.currentTimeMillis())) {
            if (work == null) stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (work != null) work.cancel(true);
        UpdateScheduler.reconcile(this);
        work = executor.submit(() -> {
            try {
                ReleaseCheck check = new ReleaseChecker(new HttpsTextFetcher()).check();
                if (stopped || Thread.currentThread().isInterrupted() || !store.monitoringEnabled()
                        || revision != store.scheduleRevision()) return;
                store.save(check);
                UpdateNotifications.consider(this, check);
                DailyPrompts.consider(this, check);
            } finally { stopSelf(startId); }
        });
        return START_NOT_STICKY;
    }

    @Override public void onTimeout(int startId) { stopped = true; stopSelf(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (work != null) work.cancel(true);
        executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
