package com.liquorbee.updater;

public final class VersionParser {
    private VersionParser() { }

    /** version.txt contains the APK's numeric Android versionCode, not a release counter. */
    public static PublishedVersion github(String text) {
        String value = text == null ? "" : text.replace("\uFEFF", "").trim();
        return new PublishedVersion(positiveLong(value), "Build " + value);
    }

    private static long positiveLong(String value) {
        if (!value.matches("[0-9]{1,19}")) throw new IllegalArgumentException("Invalid build number");
        try {
            long code = Long.parseLong(value);
            if (code <= 0) throw new IllegalArgumentException("Invalid build number");
            return code;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Build number is too large", e);
        }
    }
}
