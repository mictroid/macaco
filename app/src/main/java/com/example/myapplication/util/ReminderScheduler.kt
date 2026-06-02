package com.example.myapplication.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules/cancels the periodic "Travel Reminders" notification via WorkManager and owns the
 * notification channel. The reminder fires every [intervalDays] days; the user toggles it and picks
 * the cadence in Settings (persisted in PreferencesManager). WorkManager survives reboots and app
 * death, so the schedule keeps running without the app open.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "travel_reminders"
    const val NOTIFICATION_ID = 4001
    private const val WORK_NAME = "travel_reminder_work"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Travel reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Periodic nudges to log a new travel memory" }
            )
        }
    }

    fun schedule(context: Context, intervalDays: Int) {
        ensureChannel(context)
        val days = intervalDays.coerceAtLeast(1).toLong()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(days, TimeUnit.DAYS)
            // First nudge after a full interval, not immediately on enable.
            .setInitialDelay(days, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
