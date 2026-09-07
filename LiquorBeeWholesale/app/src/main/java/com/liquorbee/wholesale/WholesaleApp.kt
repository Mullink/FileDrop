package com.liquorbee.wholesale

import android.app.Application
import com.liquorbee.wholesale.ui.AppNotifications

/** Deliberately minimal - no heavyweight/blocking initialization here. The previous (MAUI)
 * attempt's app never launched at all and was never diagnosed; keeping Application.onCreate
 * trivial removes one whole class of "installs but won't open" bug. Notification channel creation
 * is the one exception - it's a single cheap system call, required once before any notification
 * can show at all on API 26+. */
class WholesaleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppNotifications.createChannel(this)
    }
}
