package com.houseofmmminq.macaco.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.widgetHighlight
import com.houseofmmminq.macaco.util.ImageStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OnThisDayWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Called after a successful save/delete (JournalViewModel) so the widget doesn't wait for
         *  its next periodic refresh to reflect a new/changed entry. */
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, OnThisDayWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, OnThisDayWidgetProvider::class.java).apply {
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
            val entry = uid?.let { fetchHighlight(it) }
            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, entry, signedIn = uid != null) }
            pendingResult.finish()
        }
    }

    private suspend fun fetchHighlight(uid: String): TravelEntry? = runCatching {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("entries")
            .get().await()
        // Same decode shape as CloudEntrySync.startListening — kept in sync manually since a
        // widget-process query can't share that private mapper (see the brief's Scope note).
        val entries = snapshot.documents.mapNotNull { doc ->
            runCatching {
                TravelEntry(
                    id = doc.getString("id") ?: doc.id,
                    title = doc.getString("title") ?: return@runCatching null,
                    location = doc.getString("location") ?: "",
                    dateMillis = doc.getLong("dateMillis") ?: 0L,
                    description = doc.getString("description") ?: "",
                    mood = doc.getString("mood") ?: "",
                    photoUris = (doc.get("photoUris") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                    createdAt = doc.getLong("createdAt") ?: 0L
                )
            }.getOrNull()
        }
        entries.widgetHighlight()
    }.getOrNull()

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        entry: TravelEntry?,
        signedIn: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)
        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

        when {
            !signedIn -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_signed_out_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_signed_out_subtitle))
                views.setImageViewResource(R.id.widget_photo, R.drawable.ic_launcher_foreground)
            }
            entry == null -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_subtitle))
                views.setImageViewResource(R.id.widget_photo, R.drawable.ic_launcher_foreground)
            }
            else -> {
                views.setTextViewText(R.id.widget_title, entry.title)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    entry.location.ifBlank { null } ?: context.getString(R.string.widget_no_location)
                )
                // Decoded to a small in-memory Bitmap (not setImageViewUri) so there's no
                // cross-process content:// permission question for the launcher/widget host to
                // resolve — same EXIF-safe decode pattern as JournalBackup.compressToBytes.
                val bitmap = entry.photoUris.firstOrNull()?.let { decodeThumbnail(context, it) }
                if (bitmap != null) views.setImageViewBitmap(R.id.widget_photo, bitmap)
                else views.setImageViewResource(R.id.widget_photo, R.drawable.ic_launcher_foreground)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun decodeThumbnail(context: Context, uriString: String): Bitmap? = runCatching {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) } ?: return null
        var sample = 1
        while (boundsOpts.outWidth / (sample * 2) >= 300 && boundsOpts.outHeight / (sample * 2) >= 300) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        ImageStorage.applyExifOrientation(bitmap, orientation)
    }.getOrNull()
}
