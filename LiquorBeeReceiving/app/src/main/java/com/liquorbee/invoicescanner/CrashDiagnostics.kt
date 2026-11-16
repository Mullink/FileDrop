package com.liquorbee.invoicescanner

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Build
import android.os.Bundle
import java.io.File

/** Local-only report. Never sends invoice data, credentials, or logs automatically. */
internal object CrashDiagnostics {
    @Volatile var scanContext: String = ""

    fun install(app: Application) {
        val file = File(app.filesDir, "last-crash.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file.bufferedWriter().use { writer ->
                    writer.appendLine("Receiving ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}); Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL}")
                    writer.appendLine("Thread: ${thread.name}; ${error.javaClass.name}: ${error.message?.take(1000)}")
                    error.stackTrace.take(25).forEach { writer.appendLine(it.toString()) }
                    writer.appendLine(scanContext.takeLast(6000))
                }
            } catch (_: Throwable) { /* Reporting must not replace the original crash handler. */ }
            previous?.uncaughtException(thread, error)
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity.isFinishing || !file.exists()) return
                val report = runCatching { file.reader().use { reader ->
                    val chars = CharArray(12000)
                    val count = reader.read(chars)
                    if (count > 0) String(chars, 0, count) else "No crash details recorded."
                } }.getOrNull() ?: return
                if (!file.delete()) return
                AlertDialog.Builder(activity).setTitle("Previous app crash — diagnostic details")
                    .setMessage(report).setPositiveButton("OK", null).show()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
