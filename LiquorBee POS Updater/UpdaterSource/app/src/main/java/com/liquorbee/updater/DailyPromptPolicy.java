package com.liquorbee.updater;

/** A rolling 24-hour limit, shared across builds and retained after reboot. */
public final class DailyPromptPolicy {
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;
    public static final long ATTEMPT_COOLDOWN_MS = 15L * 60L * 1000L;
    private DailyPromptPolicy() { }

    public static boolean shouldOpen(boolean monitoring, boolean automatic, boolean installed,
            long installedCode, long publishedCode, long now, long lastShown, long lastAttempt) {
        return monitoring && automatic && installed && publishedCode > installedCode
                && elapsed(now, lastShown, DAY_MS)
                && elapsed(now, lastAttempt, ATTEMPT_COOLDOWN_MS);
    }

    private static boolean elapsed(long now, long previous, long interval) {
        // If the clock moves backward, wait rather than repeatedly interrupt the cashier.
        return previous == 0 || (now >= previous && now - previous >= interval);
    }
}
