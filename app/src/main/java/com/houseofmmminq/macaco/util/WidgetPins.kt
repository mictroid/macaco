package com.houseofmmminq.macaco.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

/**
 * Lets the app ask the launcher to pin (add) one of Macaco's home-screen widgets directly,
 * so users can add a widget from Settings instead of hunting through the launcher's widget tray.
 * Backed by AppWidgetManager.requestPinAppWidget (API 26+); on older OS versions or launchers
 * that don't support pin requests it returns false so the caller can fall back to a hint.
 */
object WidgetPins {
    /** Asks the launcher to pin [provider]. Returns false if pinning isn't available here
     *  (API < 26 or a launcher without pin support) — the launcher then shows its own confirm UI. */
    fun requestPin(context: Context, provider: Class<*>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return mgr.requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
