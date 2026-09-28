package com.liquorbee.updater;

import org.junit.Test;
import static org.junit.Assert.*;

public class SelfUpdatePolicyTest {
    @Test public void newerBuildPromptsAutomaticallyOnFirstCheck() {
        assertTrue(SelfUpdatePolicy.shouldPrompt(false, 21, 20, 0, 0));
    }

    @Test public void upToDateAndOlderBuildsNeverPromptEvenManually() {
        for (boolean manual : new boolean[] { false, true }) {
            assertFalse(SelfUpdatePolicy.shouldPrompt(manual, 20, 20, 0, 0));
            assertFalse(SelfUpdatePolicy.shouldPrompt(manual, 19, 20, 0, 0));
        }
    }

    @Test public void invalidOrUnknownVersionCodesNeverPrompt() {
        for (boolean manual : new boolean[] { false, true }) {
            assertFalse(SelfUpdatePolicy.shouldPrompt(manual, 0, 20, 0, 0));
            assertFalse(SelfUpdatePolicy.shouldPrompt(manual, -1, 20, 0, 0));
            assertFalse(SelfUpdatePolicy.shouldPrompt(manual, 21, 0, 0, 0));
            assertFalse(SelfUpdatePolicy.shouldPrompt(manual, 21, -1, 0, 0));
        }
    }

    @Test public void laterSuppressesTheSameOrOlderPendingVersion() {
        assertFalse(SelfUpdatePolicy.shouldPrompt(false, 21, 20, 21, 0));
        assertFalse(SelfUpdatePolicy.shouldPrompt(false, 21, 20, 22, 0));
    }

    @Test public void anAlreadyShownVersionIsNotRepeatedDuringTheSession() {
        assertFalse(SelfUpdatePolicy.shouldPrompt(false, 21, 20, 0, 21));
        assertFalse(SelfUpdatePolicy.shouldPrompt(false, 21, 20, 0, 22));
    }

    @Test public void genuinelyNewerVersionCanPromptInTheSameSession() {
        assertTrue(SelfUpdatePolicy.shouldPrompt(false, 22, 20, 21, 21));
    }

    @Test public void manualCheckBypassesBothDismissalAndSessionSuppression() {
        assertTrue(SelfUpdatePolicy.shouldPrompt(true, 21, 20, 21, 21));
        assertTrue(SelfUpdatePolicy.shouldPrompt(true, 21, 20, 22, 22));
    }

    @Test public void buildNumbersRetainLongPrecision() {
        assertTrue(SelfUpdatePolicy.shouldPrompt(false, Long.MAX_VALUE,
                Long.MAX_VALUE - 1, 0, 0));
        assertFalse(SelfUpdatePolicy.shouldPrompt(false, Long.MAX_VALUE - 1,
                Long.MAX_VALUE, 0, 0));
    }
}
