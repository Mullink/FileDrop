package com.liquorbee.updater;

public final class UpdateConfig {
    public static final String POS_PACKAGE = "com.liquorbee.liquorbeepos";
    public static final String GITHUB_VERSION_URL =
            "https://raw.githubusercontent.com/Mullink/FileDrop/main/LiquorBeePOS/version.txt";
    public static final String APK_URL =
            "https://portal.liquorbee.com/download/liquorbeepos/imin.apk";
    public static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;
    private UpdateConfig() { }
}
