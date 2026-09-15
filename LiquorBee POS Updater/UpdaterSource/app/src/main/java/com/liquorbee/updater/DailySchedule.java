package com.liquorbee.updater;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class DailySchedule {
    public static final String CENTRAL = "America/Chicago";
    private DailySchedule() { }

    public static boolean sameDay(long first, long second, String zone) {
        return first > 0 && second > 0 && Instant.ofEpochMilli(first).atZone(ZoneId.of(zone)).toLocalDate()
                .equals(Instant.ofEpochMilli(second).atZone(ZoneId.of(zone)).toLocalDate());
    }

    public static long nextRun(long now, int hour, int minute, String zone, long lastRun) {
        ZoneId timeZone = ZoneId.of(zone);
        LocalDate date = Instant.ofEpochMilli(now).atZone(timeZone).toLocalDate();
        LocalTime time = LocalTime.of(hour, minute);
        long candidate = date.atTime(time).atZone(timeZone).toInstant().toEpochMilli();
        // One check per calendar day, including edits, clock changes and service redelivery.
        while (candidate <= now || (lastRun > 0 && (candidate <= lastRun || sameDay(candidate, lastRun, zone)))) {
            date = date.plusDays(1);
            candidate = date.atTime(time).atZone(timeZone).toInstant().toEpochMilli();
        }
        return candidate;
    }
}
