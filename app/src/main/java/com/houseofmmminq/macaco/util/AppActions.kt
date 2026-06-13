package com.houseofmmminq.macaco.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import com.houseofmmminq.macaco.R
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Growth/feedback actions backed by the Play Store: sharing the app and asking for a rating.
 * Kept UI-free so screens can call them from a click handler.
 */
object AppActions {

    // Matches the Play Console listing (build.gradle applicationId).
    private const val PACKAGE = "com.houseofmmminq.macaco"
    private const val LISTING_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    const val SUPPORT_EMAIL = "houseofmmminq@gmail.com"
    // Hosted on GitHub Pages from privacy-policy.html at the repo root.
    const val PRIVACY_POLICY_URL = "https://mictroid.github.io/macaco/privacy-policy.html"

    /** Opens the system share sheet with a link to the app's Play Store listing. */
    fun shareApp(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_app_subject))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_text, LISTING_URL))
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_app_chooser))
        )
    }

    /**
     * Asks for a rating via Google's In-App Review flow. The dialog is quota-limited and only
     * appears for Play-installed builds, so when the flow can't be shown (e.g. a sideloaded debug
     * APK) we fall back to opening the Play Store listing directly.
     */
    fun requestReview(context: Context) {
        val activity = context.findActivity() ?: run { openPlayStoreListing(context); return }
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result)
            } else {
                openPlayStoreListing(activity)
            }
        }
    }

    /** Opens the Play Store app on our listing, falling back to the browser. */
    fun openPlayStoreListing(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
        val opened = runCatching { context.startActivity(market); true }.getOrDefault(false)
        if (!opened) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LISTING_URL))) }
        }
    }

    /** Opens the user's email app with a pre-filled message to support. */
    fun contactSupport(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.help_contact_subject))
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.help_contact))
            )
        }
    }

    /** Opens [url] in the browser. */
    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun Context.findActivity(): Activity? {
        var ctx: Context = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
