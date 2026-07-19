package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R

/** 1×1 home-screen shortcut straight into the New Entry screen. Static — no data fetch,
 *  reuses the same ACTION_NEW_ENTRY deep link ReminderWorker builds for its notification. */
class QuickAddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_NEW_ENTRY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            views.setOnClickPendingIntent(R.id.widget_quick_add_root, pendingIntent)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
