package com.liquorbee.updater;

import java.io.IOException;

public final class ReleaseChecker {
    public interface TextFetcher { String get(String url, int maxBytes) throws IOException; }
    private final TextFetcher fetcher;

    public ReleaseChecker(TextFetcher fetcher) { this.fetcher = fetcher; }

    public ReleaseCheck check() {
        PublishedVersion published = null;
        String error = "";
        try {
            published = VersionParser.github(fetcher.get(UpdateConfig.GITHUB_VERSION_URL, 1024));
        } catch (IOException e) {
            error = "Could not reach GitHub. Check the connection and try again.";
        } catch (IllegalArgumentException e) {
            error = "The published version is invalid. version.txt must contain the APK build number.";
        }
        return new ReleaseCheck(published, error, System.currentTimeMillis());
    }
}
