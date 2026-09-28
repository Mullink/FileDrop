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

    public static InstalledPos read(Context context) {
        return read(context, UpdateConfig.POS_PACKAGE);
    }

    @SuppressWarnings("deprecation")
    public static InstalledPos read(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return new InstalledPos(true, info.getLongVersionCode(),
                    info.versionName == null ? "Unknown" : info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            return new InstalledPos(false, 0, "Not installed");
        }
    }
}
