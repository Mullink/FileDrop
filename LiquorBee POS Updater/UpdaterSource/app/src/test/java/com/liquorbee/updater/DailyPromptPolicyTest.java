package com.liquorbee.updater;

import org.junit.Test;
import static org.junit.Assert.*;

public class DailyPromptPolicyTest {
    private static final long NOW = 1700000000000L;

    private boolean due(long published, long shown, long attempted) {
        return DailyPromptPolicy.shouldOpen(true, true, true, 20260910, published, NOW, shown, attempted);
    }

    @Test public void firstNewBuildOpensImmediately() { assertTrue(due(20260911, 0, 0)); }

    @Test public void samePendingBuildReturnsAfterTwentyFourHours() {
        assertFalse(due(20260911, NOW - DailyPromptPolicy.DAY_MS + 1, 0));
        assertTrue(due(20260911, NOW - DailyPromptPolicy.DAY_MS, 0));
    }

    @Test public void newerBuildDoesNotBypassDailyLimit() {
        assertFalse(due(20260912, NOW - 3600000, 0));
    }

    @Test public void laterPostponesAllAutomaticOpeningsForTwentyFourHours() {
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
}
