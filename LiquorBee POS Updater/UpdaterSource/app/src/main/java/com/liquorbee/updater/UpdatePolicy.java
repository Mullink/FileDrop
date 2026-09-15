package com.liquorbee.updater;

public final class UpdatePolicy {
    private UpdatePolicy() { }

    public static boolean shouldNotify(boolean monitoring, boolean notificationsAllowed,
                                       boolean installed, long installedCode,
                                       long publishedCode, long previouslyNotifiedCode) {
        return monitoring && notificationsAllowed && installed
                && publishedCode > installedCode && publishedCode > previouslyNotifiedCode;
    }
}
