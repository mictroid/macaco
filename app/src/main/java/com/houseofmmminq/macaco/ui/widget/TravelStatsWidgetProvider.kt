package com.houseofmmminq.macaco.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.locations
import com.houseofmmminq.macaco.data.storage.toTravelEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** 2×1 card showing total entry count and distinct-place count. Same goAsync() + one-shot
 *  Firestore read pattern as OnThisDayWidgetProvider, but aggregates instead of picking one entry. */
class TravelStatsWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Called after a successful save/delete (JournalViewModel) so the counts refresh
         *  immediately rather than waiting for the next periodic update. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, TravelStatsWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, TravelStatsWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val (entryCount, placeCount) = uid?.let { fetchCounts(it) } ?: (0 to 0)
            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, entryCount, placeCount, signedIn = uid != null) }
            pendingResult.finish()
        }
    }

    private suspend fun fetchCounts(uid: String): Pair<Int, Int> = runCatching {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("entries")
            .get().await()
        val entries = snapshot.documents.mapNotNull { it.toTravelEntry() }
        entries.size to entries.locations().size
    }.getOrDefault(0 to 0)

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        entryCount: Int,
        placeCount: Int,
        signedIn: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_travel_stats)
        views.setOnClickPendingIntent(R.id.widget_stats_root, openAppPendingIntent(context))
        if (signedIn) {
            views.setTextViewText(R.id.widget_stats_entries_value, entryCount.toString())
            views.setTextViewText(R.id.widget_stats_places_value, placeCount.toString())
        } else {
            views.setTextViewText(R.id.widget_stats_entries_value, "–")
            views.setTextViewText(R.id.widget_stats_places_value, "–")
        }
        views.setTextViewText(R.id.widget_stats_entries_label, context.getString(R.string.widget_stats_entries_label))
        views.setTextViewText(R.id.widget_stats_places_label, context.getString(R.string.widget_stats_places_label))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
