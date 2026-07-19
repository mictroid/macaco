package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R

/** 4×2 scrollable list of the most recent entries. Backed by RecentEntriesWidgetService
 *  (a RemoteViewsService collection adapter); each row deep-links into that entry's detail. */
class RecentEntriesWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Called after a successful save/delete (JournalViewModel) so the list refreshes
         *  immediately rather than waiting for the next periodic update. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, RecentEntriesWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_recent_list)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_recent_entries)

            // Tapping the header opens the app to the journal — resuming the running task like the
            // launcher icon, not cold-starting a second MainActivity (see openAppPendingIntent).
            views.setOnClickPendingIntent(R.id.widget_recent_header, openAppPendingIntent(context))

            // Collection adapter. A per-widget-id data URI keeps each instance's adapter distinct.
            val serviceIntent = Intent(context, RecentEntriesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_recent_list, serviceIntent)
            views.setEmptyView(R.id.widget_recent_list, R.id.widget_recent_empty)

            // Row taps: a MUTABLE template + per-row fill-in intent (supplies EXTRA_ENTRY_ID).
            // NEW_TASK|CLEAR_TOP so the tap lands on the already-running MainActivity via onNewIntent
            // (which navigates to the entry) instead of cold-starting a second instance.
            val rowTemplate = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_ENTRY
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_recent_list, rowTemplate)

            appWidgetManager.updateAppWidget(id, views)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_recent_list)
    }
}
