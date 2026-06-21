# Macaco — AppActions: Email Body Empty in Gmail

Gmail ignores `Intent.EXTRA_TEXT` and `Intent.EXTRA_SUBJECT` on `ACTION_SENDTO` intents —
a long-standing Gmail limitation. Other email clients read those extras fine. Fix: encode
subject and body directly into the `mailto:` URI as query parameters, which Gmail respects.

---

## Fix sendEmail()

**Problem:** Current `sendEmail()` in `util/AppActions.kt` (line 130):

```kotlin
private fun sendEmail(context: Context, subjectRes: Int, body: String, chooserTitleRes: Int) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, context.getString(subjectRes))  // Gmail ignores this
        putExtra(Intent.EXTRA_TEXT, body)                               // Gmail ignores this
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(chooserTitleRes)))
    }
}
```

Gmail opens with an empty subject and body. Other clients (Outlook, default mail app) work
because they read the extras. Gmail only populates from the `mailto:` URI query parameters.

**Fix:** Build the URI with `subject` and `body` encoded inline. Drop the extras — the URI
parameters cover all clients, not just Gmail.

**File:** `util/AppActions.kt`

Replace the `sendEmail` function:

```kotlin
private fun sendEmail(context: Context, subjectRes: Int, body: String, chooserTitleRes: Int) {
    val uriString = "mailto:$SUPPORT_EMAIL" +
        "?subject=${Uri.encode(context.getString(subjectRes))}" +
        "&body=${Uri.encode(body)}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString))
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(chooserTitleRes)))
    }
}
```

No new imports needed — `Uri` is already imported.

---

## Also fix contactSupport()

**Problem:** `contactSupport()` (line 69) has the same issue — subject is lost in Gmail:

```kotlin
fun contactSupport(context: Context, subjectRes: Int = R.string.help_contact_subject) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, context.getString(subjectRes))  // Gmail ignores this
    }
    ...
}
```

**Fix:** Same pattern — encode subject in the URI:

```kotlin
fun contactSupport(context: Context, subjectRes: Int = R.string.help_contact_subject) {
    val uriString = "mailto:$SUPPORT_EMAIL" +
        "?subject=${Uri.encode(context.getString(subjectRes))}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriString))
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.help_contact)))
    }
}
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Encode subject + body in `mailto:` URI; remove `EXTRA_SUBJECT` / `EXTRA_TEXT` | `util/AppActions.kt` — `sendEmail()` |
| 2 | Same fix for subject in `contactSupport()` | `util/AppActions.kt` — `contactSupport()` |
