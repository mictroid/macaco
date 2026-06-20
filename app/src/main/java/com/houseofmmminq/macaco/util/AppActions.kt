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

    /**
     * Opens the system share sheet with a personalized blurb (entry count) and a link to the
     * app's Play Store listing on its own line, so the receiving app auto-links it.
     */
    fun shareApp(context: Context, entryCount: Int) {
        val blurb = context.resources.getQuantityString(R.plurals.share_app_text, entryCount, entryCount)
        val shareText = "$blurb\n\n$LISTING_URL"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_app_subject))
            putExtra(Intent.EXTRA_TEXT, shareText)
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

    /** Opens the user's email app with a pre-filled message to support, with an optional subject. */
    fun contactSupport(context: Context, subjectRes: Int = R.string.help_contact_subject) {
        // Encode the subject in the mailto: URI rather than EXTRA_SUBJECT — Gmail ignores the extras
        // on ACTION_SENDTO and only reads the URI query params. The URI form works for all clients.
        val uriString = "mailto:$SUPPORT_EMAIL" +
            "?subject=${Uri.encode(context.getString(subjectRes))}"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString))
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.help_contact))
            )
        }
    }

    /** Opens the user's email app pre-filled with a feature-request template + device footer. */
    fun requestFeature(context: Context) {
        val body = buildString {
            appendLine("Hi Macaco team,")
            appendLine()
            appendLine("I'd like to suggest a feature:")
            appendLine()
            appendLine("[Describe your idea here]")
            appendLine()
            appendLine("Why it would be helpful:")
            appendLine("[Explain how this would improve your experience]")
            appendLine()
            append(deviceFooter(context))
        }
        sendEmail(
            context = context,
            subjectRes = R.string.help_feedback_feature_subject,
            body = body,
            chooserTitleRes = R.string.help_request_feature
        )
    }

    /** Opens the user's email app pre-filled with a bug-report template + device footer. */
    fun reportIssue(context: Context) {
        val body = buildString {
            appendLine("Hi Macaco team,")
            appendLine()
            appendLine("I found an issue:")
            appendLine()
            appendLine("[Describe the problem here]")
            appendLine()
            appendLine("Steps to reproduce:")
            appendLine("1. ")
            appendLine("2. ")
            appendLine("3. ")
            appendLine()
            appendLine("Expected: [what should happen]")
            appendLine("Actual: [what you see instead]")
            appendLine()
            append(deviceFooter(context))
        }
        sendEmail(
            context = context,
            subjectRes = R.string.help_feedback_issue_subject,
            body = body,
            chooserTitleRes = R.string.help_report_issue
        )
    }

    private fun sendEmail(context: Context, subjectRes: Int, body: String, chooserTitleRes: Int) {
        // Encode subject + body in the mailto: URI instead of EXTRA_SUBJECT/EXTRA_TEXT — Gmail
        // ignores those extras on ACTION_SENDTO and only populates from the URI query params. The
        // URI form fills all clients; bodies here are short templates so URI length isn't a concern.
        val uriString = "mailto:$SUPPORT_EMAIL" +
            "?subject=${Uri.encode(context.getString(subjectRes))}" +
            "&body=${Uri.encode(body)}"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString))
        runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(chooserTitleRes)))
        }
    }

    /** Device/app diagnostics appended to feedback emails so support doesn't have to ask. */
    private fun deviceFooter(context: Context): String {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("?")
        return buildString {
            appendLine("___")
            appendLine("App: $versionName")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        }
    }

    /**
     * Opens the Play Store subscription-management page for this app, where the user can cancel or
     * change their plan. Falls back to the web URL if the Play Store app can't handle the intent.
     */
    fun manageSubscriptions(context: Context) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?package=$PACKAGE")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
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
