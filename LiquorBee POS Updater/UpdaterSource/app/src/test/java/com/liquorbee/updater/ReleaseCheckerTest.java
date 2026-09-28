package com.liquorbee.updater;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ReleaseCheckerTest {
    @Test public void onlyFetchesSmallGithubFile() {
        List<String> requests = new ArrayList<>();
        ReleaseCheck result = new ReleaseChecker((url, maxBytes) -> {
            requests.add(url);
            assertEquals(1024, maxBytes);
            return "20260910\n";
        }).check();
        assertFalse(result.failed());
        assertEquals(20260910, result.published.code);
        assertEquals(1, requests.size());
        assertEquals(UpdateConfig.GITHUB_VERSION_URL, requests.get(0));
        assertTrue(result.checkedAt > 0);
    }
    @Test public void networkFailureCannotProduceUpToDateResult() {
        ReleaseCheck result = new ReleaseChecker((url, maxBytes) -> {
            throw new IOException("offline");
        }).check();
        assertTrue(result.failed());
        assertNull(result.published);
        assertFalse(result.error.isEmpty());
    }
    @Test public void invalidMetadataCannotProduceUpToDateResult() {
        ReleaseCheck result = new ReleaseChecker((url, maxBytes) -> "Not Found").check();
        assertTrue(result.failed());
        assertNull(result.published);
    }
    @Test public void selfUpdateFetchesOnlyItsOwnMetadataUrl() {
        List<String> requests = new ArrayList<>();
        ReleaseCheck result = new ReleaseChecker((url, maxBytes) -> {
            requests.add(url);
            assertEquals(1024, maxBytes);
            return "20260928\n";
        }).check(UpdateConfig.UPDATER_VERSION_URL);
        assertFalse(result.failed());
        assertEquals(20260928, result.published.code);
        assertEquals(1, requests.size());
        assertEquals(UpdateConfig.UPDATER_VERSION_URL, requests.get(0));
        assertNotEquals(UpdateConfig.GITHUB_VERSION_URL, requests.get(0));
    }

    @Test public void checkingUpdaterDoesNotChangeTheDefaultPosSource() {
        List<String> requests = new ArrayList<>();
        ReleaseChecker checker = new ReleaseChecker((url, maxBytes) -> {
            requests.add(url);
            return url.equals(UpdateConfig.UPDATER_VERSION_URL) ? "20260928" : "20260910";
        });
        assertEquals(20260928, checker.check(UpdateConfig.UPDATER_VERSION_URL).published.code);
        assertEquals(20260910, checker.check().published.code);
        assertEquals(java.util.Arrays.asList(UpdateConfig.UPDATER_VERSION_URL,
                UpdateConfig.GITHUB_VERSION_URL), requests);
    }

    @Test public void updaterFailureDoesNotFallbackToOrCorruptThePosCheck() {
        List<String> requests = new ArrayList<>();
        ReleaseChecker checker = new ReleaseChecker((url, maxBytes) -> {
            requests.add(url);
            if (url.equals(UpdateConfig.UPDATER_VERSION_URL)) throw new IOException("offline");
            return "20260910";
        });
        ReleaseCheck updater = checker.check(UpdateConfig.UPDATER_VERSION_URL);
        assertTrue(updater.failed());
        assertNull(updater.published);
        assertFalse(updater.error.isEmpty());
        assertEquals(1, requests.size());
        ReleaseCheck pos = checker.check();
        assertFalse(pos.failed());
        assertEquals(20260910, pos.published.code);
        assertEquals(java.util.Arrays.asList(UpdateConfig.UPDATER_VERSION_URL,
                UpdateConfig.GITHUB_VERSION_URL), requests);
    }

    @Test public void invalidUpdaterMetadataCannotReuseAPreviousSuccessfulPosVersion() {
        ReleaseChecker checker = new ReleaseChecker((url, maxBytes) ->
                url.equals(UpdateConfig.UPDATER_VERSION_URL) ? "Not Found" : "20260910");
        assertFalse(checker.check().failed());
        ReleaseCheck updater = checker.check(UpdateConfig.UPDATER_VERSION_URL);
        assertTrue(updater.failed());
        assertNull(updater.published);
        assertFalse(updater.error.isEmpty());
    }

    @Test public void posFailureDoesNotBlockIndependentUpdaterCheck() {
        ReleaseChecker checker = new ReleaseChecker((url, maxBytes) -> {
            if (url.equals(UpdateConfig.GITHUB_VERSION_URL)) throw new IOException("offline");
            return "20260928";
        });
        assertTrue(checker.check().failed());
        ReleaseCheck updater = checker.check(UpdateConfig.UPDATER_VERSION_URL);
        assertFalse(updater.failed());
        assertEquals(20260928, updater.published.code);
    }
}
