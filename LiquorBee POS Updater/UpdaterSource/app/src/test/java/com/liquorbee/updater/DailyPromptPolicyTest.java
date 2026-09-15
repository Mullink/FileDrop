package com.liquorbee.updater;

import org.junit.Test;
import static org.junit.Assert.*;

public class DailyPromptPolicyTest {
    private static final long NOW = 1700000000000L;

    private boolean due(long published, long shown, long attempted) {
        return DailyPromptPolicy.shouldOpen(true, true, true, 20260910, published, NOW, shown, attempted);
    }

    @Test public void firstNewBuildOpensImmediately() { assertTrue(due(20260911, 0, 0)); }

    @Test public void samePendingBuildReturnsOnNextCentralCalendarDay() {
        assertFalse(due(20260911, NOW - 3600000, 0));
        assertTrue(due(20260911, NOW - 86400000, 0));
    }

    @Test public void newerBuildDoesNotBypassDailyLimit() {
        assertFalse(due(20260912, NOW - 3600000, 0));
    }

    @Test public void laterDismissesAllAutomaticOpeningsForToday() {
        assertFalse(due(20260911, NOW, 0));
        assertFalse(due(20260912, NOW, 0));
    }

    @Test public void blockedAttemptCanRetryWithoutWaitingADay() {
        assertFalse(due(20260911, 0, NOW - DailyPromptPolicy.ATTEMPT_COOLDOWN_MS + 1));
        assertTrue(due(20260911, 0, NOW - DailyPromptPolicy.ATTEMPT_COOLDOWN_MS));
    }

    @Test public void missingInvalidCurrentAndOlderBuildsDoNotOpen() {
        assertFalse(due(0, 0, 0));
        assertFalse(due(20260910, 0, 0));
        assertFalse(due(1, 0, 0));
        assertFalse(DailyPromptPolicy.shouldOpen(true, true, false, 0, 20260911, NOW, 0, 0));
    }

    @Test public void disabledMonitoringOrAutomaticOpeningPreventsLaunch() {
        assertFalse(DailyPromptPolicy.shouldOpen(false, true, true, 1, 2, NOW, 0, 0));
        assertFalse(DailyPromptPolicy.shouldOpen(true, false, true, 1, 2, NOW, 0, 0));
    }

    @Test public void clockMovingBackwardDoesNotCauseRepeatedPrompts() {
        assertFalse(due(20260911, NOW + 3600000, 0));
        assertFalse(due(20260911, 0, NOW + 3600000));
    }

    @Test public void springDstStillAllowsNextDaysReminderAfterTwentyThreeHours() {
        long yesterday = java.time.Instant.parse("2026-03-07T16:30:00Z").toEpochMilli();
        long today = java.time.Instant.parse("2026-03-08T15:30:00Z").toEpochMilli();
        assertTrue(DailyPromptPolicy.shouldOpen(true, true, true, 1, 2, today, yesterday, yesterday));
    }
}
