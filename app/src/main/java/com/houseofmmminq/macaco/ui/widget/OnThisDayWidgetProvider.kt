package com.houseofmmminq.macaco.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.houseofmmminq.macaco.MainActivity
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.data.model.TravelEntry
import com.houseofmmminq.macaco.data.model.onThisDayEntries
import com.houseofmmminq.macaco.data.storage.toTravelEntry
import com.houseofmmminq.macaco.util.ImageStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OnThisDayWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Longest-side cap (px) for the hero photo. Keeps the RemoteViews bitmap safely under the
         *  widget-update transaction limit; 400px centre-cropped fills a 2×2 card cleanly. */
        private const val HERO_MAX_PX = 400

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
            val entry = uid?.let { fetchHighlight(context, it) }
            appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id, entry, signedIn = uid != null) }
            pendingResult.finish()
        }
    }

    private suspend fun fetchHighlight(context: Context, uid: String): TravelEntry? = runCatching {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("entries")
            .get().await()
        val entries = snapshot.documents.mapNotNull { it.toTravelEntry() }

        // Photo-aware highlight: an "On This Day" match always wins (that's the widget's promise),
        // preferring one whose photo actually resolves; only when there's no anniversary match do we
        // fall back to the most-recent entry — and there too we prefer one with a showable photo, so
        // the card isn't stuck photoless just because the newest-by-date entry happens to have none.
        fun hasPhoto(e: TravelEntry) =
            WidgetPhotos.readableSource(context, e.photoUris.firstOrNull(), e.driveFileIds.firstOrNull()) != null
        val onThisDay = entries.onThisDayEntries()          // already sorted, most recent first
        val recent = entries.sortedByDescending { it.dateMillis }
        val highlight = onThisDay.firstOrNull { hasPhoto(it) }
            ?: onThisDay.firstOrNull()
            ?: recent.firstOrNull { hasPhoto(it) }
            ?: recent.firstOrNull()

        // Ensure ONLY the chosen entry's photo is cached (1–3 files), not the whole library. A
        // full download pass here can exceed this update broadcast's goAsync ANR window on a cold
        // cache; the app's own sync (JournalViewModel) still warms the full cache when it runs.
        // ensurePhotosCached no-ops quickly when the photo is already cached or Drive isn't connected.
        val app = context.applicationContext as com.houseofmmminq.macaco.TravelJournalApp
        highlight?.let { runCatching { app.drivePhotoSync.ensurePhotosCached(listOf(it)) } }
        highlight
    }.getOrNull()

    private fun updateOne(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        entry: TravelEntry?,
        signedIn: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_on_this_day)
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))

        // Show the photo + scrim only when there's an actual photo to display; otherwise the plain
        // teal card shows through (no oversized launcher icon, consistent with the other widgets).
        fun showPhoto(show: Boolean) {
            views.setViewVisibility(R.id.widget_photo, if (show) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_scrim, if (show) View.VISIBLE else View.GONE)
        }

        when {
            !signedIn -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_signed_out_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_signed_out_subtitle))
                views.setViewVisibility(R.id.widget_context, View.GONE)
                showPhoto(false)
            }
            entry == null -> {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty_subtitle))
                views.setViewVisibility(R.id.widget_context, View.GONE)
                showPhoto(false)
            }
            else -> {
                views.setTextViewText(R.id.widget_title, entry.title)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    entry.location.ifBlank { null } ?: context.getString(R.string.widget_no_location)
                )
                // The memory's own date, so the card reads as a dated memory (justifies the size
                // and clarifies the "On This Day" framing) rather than just a title + place.
                views.setTextViewText(
                    R.id.widget_context,
                    DateFormat.getDateInstance(DateFormat.LONG).format(Date(entry.dateMillis))
                )
                views.setViewVisibility(R.id.widget_context, View.VISIBLE)
                // Decoded to a small in-memory Bitmap (not setImageViewUri) so there's no
                // cross-process content:// permission question for the launcher/widget host to
                // resolve — same EXIF-safe decode pattern as JournalBackup.compressToBytes.
                val source = WidgetPhotos.readableSource(
                    context, entry.photoUris.firstOrNull(), entry.driveFileIds.firstOrNull()
                )
                val bitmap = source?.let { decodeThumbnail(context, it.toString()) }
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_photo, bitmap)
                    showPhoto(true)
                } else {
                    showPhoto(false)
                }
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun decodeThumbnail(context: Context, uriString: String): Bitmap? = runCatching {
        val uri = Uri.parse(uriString)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds pass: decodeStream returns null by design when inJustDecodeBounds is set (it only
        // fills boundsOpts), so the null-guard must be on the STREAM, not on decodeStream's result —
        // otherwise the expected-null bounds bitmap aborts the decode before the real pass runs.
        (WidgetPhotos.openStream(context, uri) ?: return null).use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        var sample = 1
        while (boundsOpts.outWidth / (sample * 2) >= HERO_MAX_PX && boundsOpts.outHeight / (sample * 2) >= HERO_MAX_PX) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = WidgetPhotos.openStream(context, uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        val orientation = WidgetPhotos.openStream(context, uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val oriented = ImageStorage.applyExifOrientation(bitmap, orientation)
        // Hard-cap the longest side. A RemoteViews bitmap that's too large silently blows the
        // widget-update Binder transaction limit, so setImageViewBitmap is dropped and the photo
        // never appears (the Recent Entries rows decode smaller, which is why those render). inSampleSize
        // only halves, so a portrait can still land ~2× over the cap — scale the result down to be sure.
        val longest = maxOf(oriented.width, oriented.height)
        if (longest > HERO_MAX_PX) {
            val scale = HERO_MAX_PX.toFloat() / longest
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else oriented
    }.getOrNull()
}
