package com.liquorbee.wholesale.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.liquorbee.wholesale.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-shot local notifications for events that finish while the user isn't necessarily looking at
 * the one screen that would otherwise show it - OCR extraction finishing (a Hangfire job running
 * server-side, surfaced today only via ScanActivity's 60s "ready for review" poll) and a manual PO
 * being created. Silently does nothing if POST_NOTIFICATIONS hasn't been granted (API 33+) - this
 * is a convenience, never something that should block or nag about a missing permission.
 */
object AppNotifications {
    private const val CHANNEL_ID = "order_updates"
    private val nextId = AtomicInteger(1000)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Order Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Invoice ready for review, purchase order created"
                }
            )
        }
    }

    fun show(context: Context, title: String, text: String, targetActivity: Class<*>? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (targetActivity != null) {
            val intent = Intent(context, targetActivity).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            builder.setContentIntent(
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(nextId.getAndIncrement(), builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check above and this call - drop it, not worth crashing over.
        }
    }
}
