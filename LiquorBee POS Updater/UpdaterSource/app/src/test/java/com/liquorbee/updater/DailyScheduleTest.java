package com.liquorbee.updater;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class DailyScheduleTest {
    private long at(String time) { return Instant.parse(time).toEpochMilli(); }
    private long next(String now, int hour, int minute, long last) {
        return DailySchedule.nextRun(at(now), hour, minute, DailySchedule.CENTRAL, last);
    }

    @Test public void defaultsToTenThirtyChicagoDuringDaylightSaving() {
        assertEquals(at("2026-09-15T15:30:00Z"), next("2026-09-15T15:29:00Z", 10, 30, 0));
    }
    @Test public void winterUsesCentralStandardOffset() {
        assertEquals(at("2026-12-15T16:30:00Z"), next("2026-12-15T15:29:00Z", 10, 30, 0));
    }
    @Test public void pastOrEqualTimeGoesToTomorrow() {
        assertEquals(at("2026-09-16T15:30:00Z"), next("2026-09-15T15:30:00Z", 10, 30, 0));
        assertEquals(at("2026-09-16T15:30:00Z"), next("2026-09-15T17:00:00Z", 10, 30, 0));
    }
    @Test public void changingTimeAfterTodaysRunCannotScheduleTwice() {
        assertEquals(at("2026-09-16T19:15:00Z"), next("2026-09-15T16:00:00Z", 14, 15, at("2026-09-15T15:30:00Z")));
    }
    @Test public void nextSpringDayIsTwentyThreeHoursLater() {
        long now = at("2026-03-07T16:30:00Z");
        assertEquals(23L * 3600000, DailySchedule.nextRun(now, 10, 30, DailySchedule.CENTRAL, now) - now);
    }
    @Test public void nextAutumnDayIsTwentyFiveHoursLater() {
        long now = at("2026-10-31T15:30:00Z");
        assertEquals(25L * 3600000, DailySchedule.nextRun(now, 10, 30, DailySchedule.CENTRAL, now) - now);
    }
    @Test public void missingSpringTimeAdvancesToValidTime() {
        assertEquals(at("2026-03-08T08:30:00Z"), next("2026-03-08T06:00:00Z", 2, 30, 0));
    }
    @Test public void repeatedAutumnTimeRunsOnlyOnce() {
        long first = at("2026-11-01T06:30:00Z");
        assertEquals(at("2026-11-02T07:30:00Z"), next("2026-11-01T06:31:00Z", 1, 30, first));
    }
    @Test(expected = java.time.DateTimeException.class) public void invalidHourIsRejected() {
        next("2026-09-15T12:00:00Z", 24, 0, 0);
    }
}
