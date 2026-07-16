package com.houseofmmminq.macaco.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R

/**
 * Posts the "your annual Macaco subscription renews in 7 days" notification. Scheduled by
 * RenewalReminderScheduler, timed off the live entitlement's expirationDate — see that file for
 * the scheduling/rescheduling logic.
 */
class RenewalReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        RenewalReminderScheduler.ensureChannel(ctx)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        // Deep-link into Subscription Info so the user can see the exact date/price immediately.
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SUBSCRIPTION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, RenewalReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.renewal_notification_title))
            .setContentText(ctx.getString(R.string.renewal_notification_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.renewal_notification_body)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(RenewalReminderScheduler.NOTIFICATION_ID, notification)
        }
        return Result.success()
    }
}
