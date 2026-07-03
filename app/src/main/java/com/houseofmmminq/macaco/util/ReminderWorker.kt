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
import com.houseofmmminq.macaco.TravelJournalApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Posts the periodic "log a travel memory" reminder. Scheduled by [ReminderScheduler] (cadence) but
 * the content is personalized from the user's own journal data via [buildNotificationCopy]:
 *  - skips quietly when signed out or when the user already logged something today,
 *  - picks copy from last location / days-since / entry count,
 *  - deep-links to the new-entry screen and offers +Add Memory / Remind me later actions.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        ReminderScheduler.ensureChannel(ctx)

        // On Android 13+ posting requires the runtime POST_NOTIFICATIONS grant. If it's missing,
        // skip quietly (the user can re-grant in Settings) rather than failing the work.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val app = ctx as TravelJournalApp

        // Don't notify if the user isn't signed in. In a freshly spawned worker process auth may not
        // have restored yet, so wait briefly for the first non-null value before giving up.
        withTimeoutOrNull(TimeUnit.SECONDS.toMillis(5)) {
            app.authRepository.currentUser.first { it != null }
        } ?: return Result.success()

        // Current entries. The Firestore listener may not have populated in a cold process, so wait
        // briefly for a non-empty list, then fall back to whatever is loaded (empty for new users).
        val entries = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(5)) {
            app.cloudEntrySync.entries.first { it.isNotEmpty() }
        } ?: app.cloudEntrySync.entries.value

        // Don't notify if the user already logged something today.
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (entries.any { it.createdAt >= todayStart }) return Result.success()

        val lastEntry = entries.maxByOrNull { it.createdAt }
        val daysSinceLast = lastEntry?.let {
            ((System.currentTimeMillis() - it.createdAt) / TimeUnit.DAYS.toMillis(1)).toInt()
        }

        val (title, body) = buildNotificationCopy(
            context = ctx,
            entryCount = entries.size,
            daysSinceLast = daysSinceLast,
            lastLocation = lastEntry?.location?.takeIf { it.isNotBlank() }
        )

        // Deep-link: open MainActivity straight to the new-entry screen.
        val newEntryIntent = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW_ENTRY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val newEntryPending = PendingIntent.getActivity(
            ctx, 0, newEntryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Remind me later" — dismiss and re-fire two hours out.
        val snoozePending = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, SnoozeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Status-bar icon is monochrome (Android uses its alpha only). No large icon — when both
        // are set Android badges the small icon into the card's top-right, duplicating it.
        val notification = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(newEntryPending)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_add, ctx.getString(R.string.reminder_action_add), newEntryPending)
            .addAction(R.drawable.ic_snooze, ctx.getString(R.string.reminder_action_snooze), snoozePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(ReminderScheduler.NOTIFICATION_ID, notification)
        }
        return Result.success()
    }
}
