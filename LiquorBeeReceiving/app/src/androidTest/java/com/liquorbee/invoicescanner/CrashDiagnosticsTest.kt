package com.liquorbee.invoicescanner

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CrashDiagnosticsTest {
    @Test fun writesLocalReportAndDelegatesToOriginalCrashHandler() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val saved = Thread.getDefaultUncaughtExceptionHandler()
        val file = File(app.filesDir, "last-crash.txt")
        var delegated = false
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
            CrashDiagnostics.install(app)
            CrashDiagnostics.scanContext = "Synthetic scan: bounded decode sample=4"
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException("synthetic-render-failure"))
            assertTrue(delegated)
            val report = file.readText()
            assertTrue(report.contains("synthetic-render-failure"))
            assertTrue(report.contains("sample=4"))
            assertTrue(report.contains(BuildConfig.VERSION_NAME))
        } finally {
            file.delete()
            CrashDiagnostics.scanContext = ""
            Thread.setDefaultUncaughtExceptionHandler(saved)
        }
    }
}
