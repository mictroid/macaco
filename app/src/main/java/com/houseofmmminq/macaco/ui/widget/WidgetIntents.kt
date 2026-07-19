package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.houseofmmminq.macaco.MainActivity

/**
 * PendingIntent for a widget's plain "open the app" tap. Must behave exactly like tapping the
 * home-screen launcher icon: resume the already-running task instead of cold-starting a second
 * MainActivity. A bare `Intent(context, MainActivity::class.java)` does NOT do that — it fails to
 * match the launcher's task-root intent, so the system creates a fresh task/instance (onCreate →
 * new ViewModel → photos re-download), which is exactly the "widget reloads the app" bug. Matching
 * the launcher intent (ACTION_MAIN + CATEGORY_LAUNCHER + RESET_TASK_IF_NEEDED) makes the tap resume
 * the existing task. Shared by On This Day, Travel Stats, and the Recent Entries header.
 */
internal fun openAppPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
    return PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
