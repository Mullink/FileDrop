package com.liquorbee.invoicescanner.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.InvoiceOcrScanBatchStatusDto
import com.liquorbee.invoicescanner.network.SessionManager

/**
 * Shared status-transition tracking, checked from both ScanActivity (the screen someone's
 * actually watching right after submitting) and HomeActivity (the screen a POS terminal is far
 * more likely to be sitting on by the time the server-side OCR job actually finishes).
 *
 * GetMyBatches returns EVERY batch ever created for the customer, not just recent ones, so this
 * can't just be "notify once per batch id the first time it shows up as done" - that would fire a
 * notification for every already-long-finished historical batch the moment the app is opened.
 * Instead it tracks each batch's last-seen status and only notifies on a genuine transition INTO a
 * notify-worthy status - the very first poll after the app opens just silently records a baseline
 * with no notifications, and only a status change after that baseline counts.
 *
 * Two notify-worthy outcomes exist because whether a scan lands on ReadyForReview or goes straight
 * to Staged depends on the customer's AutoStageWithoutReview setting - a customer with that setting
 * on never produces a ReadyForReview batch at all, so watching only for that status silently never
 * notifies for them.
 */
object ReadyForReviewNotifier {
    private var initialized = false
    private val lastKnownStatus = mutableMapOf<String, String>()

    suspend fun checkAndNotify(context: Context, session: SessionManager): List<InvoiceOcrScanBatchStatusDto> {
        val batches = try {
            val api = ApiClient.buildAuthenticatedApi(session)
            api.getMyBatches()
        } catch (e: Exception) {
            return emptyList() // silent - this is a background poll, not a user action
        }

        if (!initialized) {
            batches.forEach { lastKnownStatus[it.batchId] = it.status.orEmpty() }
            initialized = true
        } else {
            for (batch in batches) {
                val previousStatus = lastKnownStatus[batch.batchId]
                val currentStatus = batch.status.orEmpty()
                if (previousStatus != currentStatus) {
                    val title = when (currentStatus) {
                        "ReadyForReview" -> "Invoice ready for review"
                        "Staged" -> "Invoice staged"
                        else -> null
                    }
                    if (title != null) {
                        val vendor = batch.extractedVendorName ?: "Unknown Vendor"
                        AppNotifications.show(context, title, vendor, ScanActivity::class.java)
                        showInAppAlert(context, title, vendor)
                    }
                }
                lastKnownStatus[batch.batchId] = currentStatus
            }
        }

        return batches.filter { it.status == "ReadyForReview" }
    }

    // The system notification alone is easy to miss while the app is already open and in someone's
    // hand - this pops an on-screen dialog too, but only when the calling screen (ScanActivity or
    // HomeActivity, whichever is polling) is actually the one currently in the foreground. Checking
    // Lifecycle.State.RESUMED (not just isFinishing/isDestroyed) matters here because BOTH screens
    // poll independently - if the other one is merely stopped in the background (not destroyed),
    // showing a dialog on it would crash with a BadTokenException since its window isn't attached.
    private fun showInAppAlert(context: Context, title: String, message: String) {
        val owner = context as? LifecycleOwner ?: return
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
