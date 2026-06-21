# Macaco — AppActions + HelpAboutScreen: Pre-filled Email Body Templates

"Request a feature" and "Report an issue" open an email with a subject line but an empty body.
Add a template body to each so users have a clear structure to fill in. Auto-append device/app
info at the bottom so support doesn't have to ask for it.

---

## 1. Add requestFeature() and reportIssue() to AppActions

**Problem:** Both buttons call `AppActions.contactSupport(context, R.string.help_feedback_*_subject)`,
which sets `EXTRA_SUBJECT` but never sets `EXTRA_TEXT` — the body is empty.

**Fix:** Add two dedicated functions that build a template body with `EXTRA_TEXT` and auto-append
device diagnostics. Keep the existing `contactSupport` for the general "Contact support" row.

**File:** `util/AppActions.kt`

Add a private helper that builds the device footer, then two public functions. Insert after
`contactSupport()`:

```kotlin
/** Opens the user's email app pre-filled with a feature request template. */
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

/** Opens the user's email app pre-filled with a bug report template. */
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
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, context.getString(subjectRes))
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(chooserTitleRes)))
    }
}

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
```

No new imports needed — `Intent`, `Uri`, `Context` are already imported.

---

## 2. Wire up the new functions in HelpAboutScreen

**Problem:** Both `FeedbackCard` `onClick` lambdas call `contactSupport` with a subject override.

**File:** `ui/screens/HelpAboutScreen.kt`

Find the two `FeedbackCard` calls (around line 180–191):

```kotlin
FeedbackCard(
    icon = Icons.Filled.Lightbulb,
    label = stringResource(R.string.help_request_feature),
    modifier = Modifier.weight(1f),
    onClick = { AppActions.contactSupport(context, R.string.help_feedback_feature_subject) }
)
FeedbackCard(
    icon = Icons.Filled.BugReport,
    label = stringResource(R.string.help_report_issue),
    modifier = Modifier.weight(1f),
    onClick = { AppActions.contactSupport(context, R.string.help_feedback_issue_subject) }
)
```

Replace the `onClick` lambdas:

```kotlin
FeedbackCard(
    icon = Icons.Filled.Lightbulb,
    label = stringResource(R.string.help_request_feature),
    modifier = Modifier.weight(1f),
    onClick = { AppActions.requestFeature(context) }
)
FeedbackCard(
    icon = Icons.Filled.BugReport,
    label = stringResource(R.string.help_report_issue),
    modifier = Modifier.weight(1f),
    onClick = { AppActions.reportIssue(context) }
)
```

No new imports needed.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `requestFeature()`, `reportIssue()`, `sendEmail()`, `deviceFooter()` | `util/AppActions.kt` |
| 2 | Update two `FeedbackCard` `onClick` lambdas to call new functions | `ui/screens/HelpAboutScreen.kt` |
