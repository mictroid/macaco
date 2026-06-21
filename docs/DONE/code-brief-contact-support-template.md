# Macaco — AppActions + HelpAboutScreen: Contact Support Template + ToS Link

Two fixes: (1) `contactSupport()` in `AppActions.kt` opens a blank email body — add a template.
(2) `HelpAboutScreen.kt` already has a Privacy Policy row but is missing a Terms of Service row
— add one directly beneath it.

---

## Fix: add body template to contactSupport()

**Problem:** `contactSupport()` builds a `mailto:` URI with only `?subject=…` — no `&body=`
param. Every email client (including Gmail) opens a blank compose window.

The other two email helpers (`requestFeature`, `reportIssue`) already use the private
`sendEmail()` function, which encodes both subject and body in the URI so Gmail picks them up.
`contactSupport()` predates that helper and was never updated.

**Fix:** Replace the manual URI construction in `contactSupport()` with a call to `sendEmail()`,
providing a minimal free-form template and the device footer.

### Replace the current `contactSupport()` implementation:

```kotlin
// BEFORE:
fun contactSupport(context: Context, subjectRes: Int = R.string.help_contact_subject) {
    val uriString = "mailto:$SUPPORT_EMAIL" +
        "?subject=${Uri.encode(context.getString(subjectRes))}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString))
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.help_contact))
        )
    }
}

// AFTER:
fun contactSupport(context: Context, subjectRes: Int = R.string.help_contact_subject) {
    val body = buildString {
        appendLine("Hi Macaco team,")
        appendLine()
        appendLine("[Write your message here]")
        appendLine()
        append(deviceFooter(context))
    }
    sendEmail(
        context = context,
        subjectRes = subjectRes,
        body = body,
        chooserTitleRes = R.string.help_contact
    )
}
```

No imports needed — `sendEmail()` and `deviceFooter()` are already private members of the
same `AppActions` object.

---

## 2. Add Terms of Service row to HelpAboutScreen

**Problem:** The Help screen has a Privacy Policy row but no Terms of Service link, even though
the ToS is now a live document that users may want to review.

**Fix:** Add one `HelpActionRow` for Terms of Service immediately after the existing Privacy
Policy row. `AppActions.TERMS_URL` is already defined; no new constants needed.

In `HelpAboutScreen.kt`, find the Privacy Policy `HelpActionRow` (around line 210) and insert
the ToS row directly after it:

```kotlin
HelpActionRow(
    icon = Icons.Filled.PrivacyTip,
    title = stringResource(R.string.help_privacy_policy),
    subtitle = stringResource(R.string.help_privacy_policy_subtitle),
    onClick = { AppActions.openUrl(context, AppActions.PRIVACY_POLICY_URL) }
)
// ADD THIS:
HelpActionRow(
    icon = Icons.Outlined.Gavel,
    title = stringResource(R.string.help_terms_of_service),
    subtitle = stringResource(R.string.help_terms_of_service_subtitle),
    onClick = { AppActions.openUrl(context, AppActions.TERMS_URL) }
)
```

Add the import if not already present:
```kotlin
import androidx.compose.material.icons.outlined.Gavel
```

### New string resources

| Key | EN value |
|-----|----------|
| `help_terms_of_service` | Terms of Service |
| `help_terms_of_service_subtitle` | Review your agreement with Macaco |

Add to `strings.xml` ×11 languages.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Rewrite `contactSupport()` to call `sendEmail()` with a template body | `util/AppActions.kt` |
| 2 | Add Terms of Service `HelpActionRow` after the Privacy Policy row | `ui/screens/HelpAboutScreen.kt` |
| 3 | Add `help_terms_of_service` + `help_terms_of_service_subtitle` strings | `strings.xml` ×11 |
