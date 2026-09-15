package com.liquorbee.updater;

import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

public final class DailyPrompts {
    static final String EXTRA_TOKEN = "com.liquorbee.updater.DAILY_PROMPT_TOKEN";
    private DailyPrompts() { }

    public static boolean launchAllowed(Context context) {
        // Android's documented SYSTEM_ALERT_WINDOW background-activity exemption.
        // No overlays, accessibility service, or full-screen notification are used.
        return Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context);
    }

    public static synchronized void consider(Context context, ReleaseCheck check) {
        if (check == null || check.failed() || !launchAllowed(context)) return;
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (power == null || !power.isInteractive() || keyguard == null || keyguard.isKeyguardLocked()) return;
        InstalledPos installed = InstalledPos.read(context);
        UpdateStore store = new UpdateStore(context);
        long now = System.currentTimeMillis();
        if (!DailyPromptPolicy.shouldOpen(store.monitoringEnabled(), store.dailyOpeningEnabled(),
                installed.installed, installed.code, check.published.code, now,
                store.dailyShownAt(), store.dailyAttemptAt())) return;
        String token = store.beginDailyAttempt(now);
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_TOKEN, token);
        try {
            context.startActivity(intent);
            // A background launch can be silently blocked by Android/OEM policy.
            // Only MainActivity confirming its resume consumes the 24-hour allowance.
        } catch (SecurityException | ActivityNotFoundException ignored) {
            // The per-build notification remains available; a later job can retry.
        }
    }

    public static synchronized void confirmOpened(Context context, Intent intent) {
        if (intent == null) return;
        new UpdateStore(context).confirmDailyOpened(intent.getStringExtra(EXTRA_TOKEN), System.currentTimeMillis());
        intent.removeExtra(EXTRA_TOKEN);
    }

    public static synchronized void recordReview(Context context, ReleaseCheck check) {
        InstalledPos installed = InstalledPos.read(context);
        if (installed.installed && check != null && !check.failed() && check.published.code > installed.code) {
            new UpdateStore(context).deferDailyOpening(System.currentTimeMillis());
        }
    }
}
