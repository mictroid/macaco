package com.houseofmmminq.macaco.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Handles the "Remind me later" action on the journal reminder notification: dismiss the current
 * notification and re-fire [ReminderWorker] once, two hours out.
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(ReminderScheduler.NOTIFICATION_ID)

        val snoozeRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueue(snoozeRequest)
    }
}
