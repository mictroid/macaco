package com.houseofmmminq.macaco.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.houseofmmminq.macaco.R
import java.util.concurrent.TimeUnit

/**
 * Schedules the one-time "your annual subscription renews soon" notification, timed to land 7
 * days before the active entitlement's expirationDate. Rescheduled by BillingManager.applyEntitlement
 * every time the entitlement refreshes (login, resume, push update), so it stays correct if the
 * renewal date ever shifts (e.g. a plan change). Monthly subscribers never get this — see
 * RevenueCatConfig.ANNUAL_BASE_PLAN_ID gating in BillingManager.
 */
object RenewalReminderScheduler {
    const val CHANNEL_ID = "subscription_renewal"
    const val NOTIFICATION_ID = 4002
    private const val WORK_NAME = "annual_renewal_reminder_work"
    private val LEAD_TIME_MILLIS = TimeUnit.DAYS.toMillis(7)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.renewal_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.renewal_channel_desc) }
            )
        }
    }

    /** [expirationMillis] is the entitlement's expirationDate (epoch millis). */
    fun schedule(context: Context, expirationMillis: Long) {
        ensureChannel(context)
        val fireAt = expirationMillis - LEAD_TIME_MILLIS
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) {
            // Already within 7 days of renewal (or past it) — don't fire a stale/late reminder.
            cancel(context)
            return
        }
        val request = OneTimeWorkRequestBuilder<RenewalReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
