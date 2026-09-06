package com.liquorbee.invoicescanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Is a newer build available" check against the GitHub repo the debug APK gets pushed to
 * (https://github.com/Mullink/FileDrop). GitHub doesn't expose a real APK version any other way
 * without downloading and parsing the whole APK, so this instead reads a small companion text
 * file - VERSION_TXT_URL - that must contain just the latest build's integer versionCode as plain
 * text. That file has to be created/updated by hand (or by whatever pushes app-debug.apk there)
 * every time a new APK is pushed - there is no way to automate that from this app's side alone.
 */
object UpdateChecker {
    private const val VERSION_TXT_URL = "https://raw.githubusercontent.com/Mullink/FileDrop/main/version.txt"
    private const val PREFS_NAME = "update_checker"
    private const val KEY_DISMISSED_VERSION = "dismissedVersionCode"
    // The raw content URL, not the github.com "blob" page - raw.githubusercontent.com serves the
    // actual APK bytes directly (confirmed Content-Type: application/octet-stream), so tapping
    // Download goes straight into the browser's own download flow instead of landing on a GitHub
    // page the user then has to find and tap a second "Download raw file" button on.
    const val APK_DOWNLOAD_URL = "https://raw.githubusercontent.com/Mullink/FileDrop/main/app-debug.apk"

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

    // Shows a dialog either way - "Update Available" with a Download action, or a quick "You're up
    // to date" confirmation. Used by both the periodic auto-check and the manual sync tap; silent
    // is only for CheckFailed on the automatic path (see HomeActivity) - a manual tap always shows
    // something so tapping Sync is never a no-op from the user's point of view.
    fun showResult(context: Context, result: Result, announceUpToDateAndFailure: Boolean) {
        when (result) {
            is Result.UpdateAvailable -> {
                AlertDialog.Builder(context)
                    .setTitle("Update Available")
                    .setMessage("A newer version of this app is available. Please download and install it.")
                    .setPositiveButton("Download") { _, _ ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_DOWNLOAD_URL)))
                    }
                    // Remembered across app restarts (see wasDismissed) - tapping Later means "stop
                    // nagging me about THIS version"; a genuinely newer version still gets its own
                    // fresh popup once it's out.
                    .setNegativeButton("Later") { _, _ -> markDismissed(context, result.latestVersionCode) }
                    .show()
            }
            Result.UpToDate -> if (announceUpToDateAndFailure) {
                AlertDialog.Builder(context)
                    .setTitle("Up to Date")
                    .setMessage("You're running the latest version.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            Result.CheckFailed -> if (announceUpToDateAndFailure) {
                AlertDialog.Builder(context)
                    .setTitle("Couldn't Check")
                    .setMessage("Couldn't check for an update right now - check your connection and try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    // Whether the user already tapped "Later" on this exact version (or a newer one, which
    // shouldn't happen but is handled the same way) - the automatic background poll checks this
    // before auto-popping so it doesn't re-nag every 5 minutes or every app relaunch.
    fun wasDismissed(context: Context, versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DISMISSED_VERSION, -1) >= versionCode
    }

    private fun markDismissed(context: Context, versionCode: Int) {
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
