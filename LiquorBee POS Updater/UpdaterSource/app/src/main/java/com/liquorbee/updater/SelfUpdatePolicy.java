package com.liquorbee.updater;

/** Keep updater prompts independent from POS monitoring and its daily reminders. */
public final class SelfUpdatePolicy {
    private SelfUpdatePolicy() { }

    public static boolean shouldPrompt(boolean manual, long latestCode, long installedCode,
                                       long dismissedCode, long promptedThisSessionCode) {
        return installedCode > 0 && latestCode > installedCode
                && (manual || (latestCode > dismissedCode && latestCode > promptedThisSessionCode));
    }
}
