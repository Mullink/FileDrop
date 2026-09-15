package com.liquorbee.updater;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class DailySchedule {
    public static final String CENTRAL = "America/Chicago";
    private DailySchedule() { }

    public static long resolveNextRun(long now, int hour, int minute, String zone,
            long lastRun, long pending, boolean timeSaved) {
        if (timeSaved) {
            ZoneId timeZone = ZoneId.of(zone);
            long today = Instant.ofEpochMilli(now).atZone(timeZone).toLocalDate()
                    .atTime(hour, minute).atZone(timeZone).toInstant().toEpochMilli();
            // Saving 4:01 at 4:01:20 means check shortly, not tomorrow.
            if (now >= today && now - today < 60000L) return now + 1000L;
            return nextRun(now, hour, minute, zone, 0);
        }
        // Opening the app near the alarm time must not replace a pending delivery with tomorrow.
        if (pending > 0 && pending > lastRun && !sameDay(pending, lastRun, zone)
                && (pending > now || now - pending <= 15L * 60000L)) return pending;
        return nextRun(now, hour, minute, zone, lastRun);
    }

    public static boolean sameDay(long first, long second, String zone) {
        return first > 0 && second > 0 && Instant.ofEpochMilli(first).atZone(ZoneId.of(zone)).toLocalDate()
                .equals(Instant.ofEpochMilli(second).atZone(ZoneId.of(zone)).toLocalDate());
    }

    public static long nextRun(long now, int hour, int minute, String zone, long lastRun) {
        ZoneId timeZone = ZoneId.of(zone);
        LocalDate date = Instant.ofEpochMilli(now).atZone(timeZone).toLocalDate();
        LocalTime time = LocalTime.of(hour, minute);
        long candidate = date.atTime(time).atZone(timeZone).toInstant().toEpochMilli();
        // Automatic repeats run once per calendar day. An explicit time save rearms separately.
        while (candidate <= now || (lastRun > 0 && (candidate <= lastRun || sameDay(candidate, lastRun, zone)))) {
            date = date.plusDays(1);
            candidate = date.atTime(time).atZone(timeZone).toInstant().toEpochMilli();
        }
        return candidate;
    }
}
