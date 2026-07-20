package com.houseofmmminq.macaco.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.storage.toTravelEntry
import com.houseofmmminq.macaco.util.ImageStorage
import java.text.DateFormat
import java.util.Date

/** Collection-adapter backing for RecentEntriesWidgetProvider. onDataSetChanged runs on a binder
 *  thread, so the one-shot Firestore read is blocked-on directly (Tasks.await) — same read path as
 *  OnThisDayWidgetProvider, just ordered + limited to the most recent entries. */
class RecentEntriesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RecentEntriesFactory(applicationContext)
}

private const val MAX_ROWS = 8

private class RecentEntriesFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var entries: List<TravelEntry> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        entries = if (uid == null) emptyList() else runCatching {
            val snapshot = Tasks.await(
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid).collection("entries")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(MAX_ROWS.toLong())
                    .get()
            )
            val result = snapshot.documents.mapNotNull { it.toTravelEntry() }
            // Pull any Drive-only photos into the local cache before the rows decode them.
            // onDataSetChanged runs on the RemoteViewsFactory binder thread (same reason the
            // Firestore read above blocks via Tasks.await), so runBlocking here is safe — not the
            // main thread, no ANR risk. Without this the widget shows placeholders on a device
            // where these entries' photos were never synced by MainActivity.
            val app = context.applicationContext as com.houseofmmminq.macaco.TravelJournalApp
            runCatching {
                kotlinx.coroutines.runBlocking { app.drivePhotoSync.ensurePhotosCached(result) }
            }
            result
        }.getOrDefault(emptyList())
    }

    override fun onDestroy() { entries = emptyList() }
    override fun getCount(): Int = entries.size
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getViewAt(position: Int): RemoteViews {
        val entry = entries[position]
        val views = RemoteViews(context.packageName, R.layout.widget_recent_entry_item)
        views.setTextViewText(R.id.recent_item_title, entry.title)
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.dateMillis))
        val metaParts = buildList {
            if (entry.mood.isNotBlank()) add(entry.mood)
            entry.weatherCode?.let { code ->
                val (icon, _) = com.houseofmmminq.macaco.util.WeatherLookup.describe(context, code)
                val temp = entry.weatherTempMaxC?.let {
                    com.houseofmmminq.macaco.util.WeatherLookup.formatTemp(
                        context, it, entry.weatherIsFahrenheit ?: false
                    )
                }
                add(if (temp != null) "$icon $temp" else icon)
            }
            entry.tags.firstOrNull()?.let { add("#$it") }
        }
        val subtitleParts = buildList {
            if (entry.location.isNotBlank()) add(entry.location)
            add(date)
            addAll(metaParts)
        }
        views.setTextViewText(R.id.recent_item_subtitle, subtitleParts.joinToString(" · "))
        val source = WidgetPhotos.readableSource(
            context, entry.photoUris.firstOrNull(), entry.driveFileIds.firstOrNull()
        )
        val bitmap = source?.let { decodeThumbnail(it.toString()) }
        if (bitmap != null) views.setImageViewBitmap(R.id.recent_item_photo, bitmap)
        else views.setImageViewResource(R.id.recent_item_photo, R.drawable.ic_launcher_foreground)
        views.setOnClickFillInIntent(
            R.id.recent_item_root,
            Intent().putExtra(MainActivity.EXTRA_ENTRY_ID, entry.id)
        )
        return views
    }

    /** Small EXIF-corrected thumbnail decode — mirrors OnThisDayWidgetProvider.decodeThumbnail
     *  but targets a ~150px row thumbnail rather than the larger hero image. */
    private fun decodeThumbnail(uriString: String): Bitmap? = runCatching {
        val uri = Uri.parse(uriString)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Null-guard the stream, not decodeStream's result: with inJustDecodeBounds it returns null
        // by design, so `?: return null` on the result would abort before the real decode.
        (WidgetPhotos.openStream(context, uri) ?: return null).use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        var sample = 1
        while (boundsOpts.outWidth / (sample * 2) >= 150 && boundsOpts.outHeight / (sample * 2) >= 150) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = WidgetPhotos.openStream(context, uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        val orientation = WidgetPhotos.openStream(context, uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        ImageStorage.applyExifOrientation(bitmap, orientation)
    }.getOrNull()
}
