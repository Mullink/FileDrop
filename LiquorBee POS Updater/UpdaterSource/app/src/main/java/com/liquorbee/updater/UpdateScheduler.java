package com.liquorbee.updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class UpdateScheduler {
    static final int JOB_ID = 48101; // Cancel the legacy hourly job on upgrade.
    static final int ALARM_ID = 48104;
    static final String EXTRA_SCHEDULED_AT = "scheduled_at";
    private UpdateScheduler() { }

    public static boolean exactAllowed(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        return alarms != null && (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms());
    }

    static PendingIntent alarmIntent(Context context, long when) {
        return PendingIntent.getForegroundService(context, ALARM_ID,
                new Intent(context, ScheduledCheckService.class).putExtra(EXTRA_SCHEDULED_AT, when),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static synchronized boolean reconcile(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs != null) jobs.cancel(JOB_ID);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        UpdateStore store = new UpdateStore(context);
        if (alarms == null) { store.setNextScheduledAt(0); return false; }
        if (!store.monitoringEnabled() || !exactAllowed(context)) {
            alarms.cancel(alarmIntent(context, 0));
            store.setNextScheduledAt(0);
            return !store.monitoringEnabled();
        }
        long when = DailySchedule.nextRun(System.currentTimeMillis(), store.dailyHour(), store.dailyMinute(),
                DailySchedule.CENTRAL, store.scheduledCheckAt());
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarmIntent(context, when));
            store.setNextScheduledAt(when);
            return true;
        } catch (SecurityException e) {
            store.setNextScheduledAt(0);
            return false;
        }
    }
}
