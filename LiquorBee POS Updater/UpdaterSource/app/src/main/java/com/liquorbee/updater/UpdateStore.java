package com.liquorbee.updater;

import android.content.Context;
import android.content.SharedPreferences;

public final class UpdateStore {
    private final SharedPreferences prefs;

    public UpdateStore(Context context) {
        prefs = context.getSharedPreferences("liquorbee_updater", Context.MODE_PRIVATE);
    }

    public boolean monitoringEnabled() { return prefs.getBoolean("monitoring", true); }
    public boolean dailyOpeningEnabled() { return prefs.getBoolean("daily_opening", true); }
    public void setDailyOpening(boolean enabled) { prefs.edit().putBoolean("daily_opening", enabled).apply(); }
    public long dailyShownAt() { return prefs.getLong("daily_shown_at", 0); }
    public long dailyAttemptAt() { return prefs.getLong("daily_attempt_at", 0); }

    public String beginDailyAttempt(long now) {
        synchronized (DailyPrompts.class) {
            String token = java.util.UUID.randomUUID().toString();
            prefs.edit().putLong("daily_attempt_at", now).putString("daily_pending_token", token).apply();
            return token;
        }
    }

    public boolean confirmDailyOpened(String token, long now) {
        synchronized (DailyPrompts.class) {
            if (token == null || !token.equals(prefs.getString("daily_pending_token", null))) return false;
            deferDailyOpening(now);
            return true;
        }
    }

    public void deferDailyOpening(long now) {
        synchronized (DailyPrompts.class) {
            prefs.edit().putLong("daily_shown_at", now).remove("daily_pending_token").apply();
        }
    }

    public void setMonitoring(boolean enabled) { prefs.edit().putBoolean("monitoring", enabled).apply(); }
    public boolean notificationExplained() { return prefs.getBoolean("notification_explained", false); }
    public void markNotificationExplained() { prefs.edit().putBoolean("notification_explained", true).apply(); }
    public long notifiedCode() { return prefs.getLong("notified_code", 0); }
    public void markNotified(long code) {
        synchronized (UpdateNotifications.class) {
            // A stale review screen must not undo a newer background notification.
            prefs.edit().putLong("notified_code", Math.max(code, notifiedCode())).apply();
        }
    }

    public void save(ReleaseCheck check) {
        prefs.edit().putLong("checked_at", check.checkedAt)
                .putLong("published_code", check.published == null ? 0 : check.published.code)
                .putString("published_label", check.published == null ? "" : check.published.label)
                .putString("check_error", check.error).apply();
    }

    public ReleaseCheck lastCheck() {
        long checked = prefs.getLong("checked_at", 0);
        if (checked == 0) return null;
        long code = prefs.getLong("published_code", 0);
        PublishedVersion version = code > 0
                ? new PublishedVersion(code, prefs.getString("published_label", "Build " + code)) : null;
        return new ReleaseCheck(version, prefs.getString("check_error", ""), checked);
    }
}
