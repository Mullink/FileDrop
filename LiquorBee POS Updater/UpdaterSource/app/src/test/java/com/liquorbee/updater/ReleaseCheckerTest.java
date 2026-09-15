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
}
