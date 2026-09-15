package com.liquorbee.updater;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    @Test public void newPosReleaseNotifiesOnceAcrossRestarts() {
        assertTrue(UpdatePolicy.shouldNotify(true, true, true, 20260910, 20260911, 0));
        assertFalse(UpdatePolicy.shouldNotify(true, true, true, 20260910, 20260911, 20260911));
        assertTrue(UpdatePolicy.shouldNotify(true, true, true, 20260910, 20260912, 20260911));
    }
    @Test public void sameAndOlderBuildsDoNotNotify() {
        assertFalse(UpdatePolicy.shouldNotify(true, true, true, 20260910, 20260910, 0));
        assertFalse(UpdatePolicy.shouldNotify(true, true, true, 20260910, 1, 0));
        assertFalse(UpdatePolicy.shouldNotify(true, true, true, 20260911, 20260910, 0));
    }
    @Test public void requiresInstalledPosAndPermissionAndMonitoring() {
        assertFalse(UpdatePolicy.shouldNotify(true, true, false, 0, 20260911, 0));
        assertFalse(UpdatePolicy.shouldNotify(true, false, true, 20260910, 20260911, 0));
        assertFalse(UpdatePolicy.shouldNotify(false, true, true, 20260910, 20260911, 0));
    }
    @Test public void comparesNumericallyRatherThanAlphabetically() {
        assertTrue(UpdatePolicy.shouldNotify(true, true, true, 9, 10, 0));
    }
}
