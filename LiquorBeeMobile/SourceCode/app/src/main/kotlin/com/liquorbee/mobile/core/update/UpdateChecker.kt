package com.liquorbee.mobile.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Is a newer build available" check against the GitHub repo the debug APK gets pushed to
 * (https://github.com/Mullink/FileDrop/tree/main/LiquorBeeMobile) - same pattern as
 * LiquorBeeInvoiceScannerAndroid's UpdateChecker. GitHub doesn't expose a real APK version any
 * other way without downloading and parsing the whole APK, so this instead reads a small
 * companion text file containing just the latest build's integer versionCode as plain text - that
 * file has to be updated by hand every time a new APK is pushed there.
 */
object UpdateChecker {
    private const val VERSION_TXT_URL = "https://raw.githubusercontent.com/Mullink/FileDrop/main/LiquorBeeMobile/version.txt"
    const val APK_DOWNLOAD_URL = "https://raw.githubusercontent.com/Mullink/FileDrop/main/LiquorBeeMobile/app-debug.apk"

    private const val PREFS_NAME = "update_checker"
    private const val KEY_DISMISSED_VERSION = "dismissedVersionCode"

    sealed class Result {
        object UpToDate : Result()
        data class UpdateAvailable(val latestVersionCode: Int) : Result()
        object CheckFailed : Result()
    }

    suspend fun check(context: Context): Result {
        val installedVersionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode
            }
        } catch (e: Exception) {
            return Result.CheckFailed
        }

        val latestVersionCode = fetchLatestVersionCode() ?: return Result.CheckFailed
        return if (latestVersionCode > installedVersionCode) Result.UpdateAvailable(latestVersionCode) else Result.UpToDate
    }

    // Persists which versionCode the user last tapped "Later" on, so the automatic background poll
    // doesn't keep re-interrupting with the same dialog every 5 minutes (or every app relaunch) -
    // only a genuinely NEWER version than the one they dismissed gets a fresh popup.
    fun wasDismissed(context: Context, versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DISMISSED_VERSION, -1) >= versionCode
    }

    fun markDismissed(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_DISMISSED_VERSION, versionCode).apply()
    }

    private suspend fun fetchLatestVersionCode(): Int? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(VERSION_TXT_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
