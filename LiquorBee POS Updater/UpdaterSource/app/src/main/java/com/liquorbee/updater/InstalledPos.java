package com.liquorbee.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public final class InstalledPos {
    public final boolean installed;
    public final long code;
    public final String name;

    private InstalledPos(boolean installed, long code, String name) {
        this.installed = installed;
        this.code = code;
        this.name = name;
    }

    @SuppressWarnings("deprecation")
    public static InstalledPos read(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(UpdateConfig.POS_PACKAGE, 0);
            return new InstalledPos(true, info.getLongVersionCode(),
                    info.versionName == null ? "Unknown" : info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            return new InstalledPos(false, 0, "Not installed");
        }
    }
}
