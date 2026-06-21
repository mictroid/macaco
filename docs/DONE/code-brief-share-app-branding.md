# Macaco — AppActions: Branded share card with monkey emoji

`shareApp()` currently sends a plain text intent. Two improvements: (1) monkey/travel emoji in
the share text so it looks fun in WhatsApp/iMessage previews, and (2) the Macaco app icon shared
as an image so WhatsApp shows it visually with the share text as the caption.

---

## 1. Enrich share text strings with emoji

**Problem:** The share blurb reads like a generic app store pitch with no personality.

**Fix:** Prepend a monkey emoji line and liven up the copy in all 11 locales.

**File:** `res/values/strings.xml` (and the 10 translated `values-*/strings.xml` files)

| Key | Current EN value | New EN value |
|-----|-----------------|--------------|
| `share_app_subject` | `Macaco — Travel Journal` | `🐒 Macaco — Travel Journal` |
| `share_app_text` (one) | `I've logged %d travel memory in Macaco — the best way to journal your adventures.` | `🐒 I've been keeping a travel journal in Macaco and logged %d memory so far!\n\nCapture your adventures, moods, and favourite moments — all in one place. 🌍✈️📖` |
| `share_app_text` (other) | `I've logged %d travel memories in Macaco — the best way to journal your adventures.` | `🐒 I've been keeping a travel journal in Macaco and logged %d memories so far!\n\nCapture your adventures, moods, and favourite moments — all in one place. 🌍✈️📖` |

Update all 11 `values-*/strings.xml` files accordingly, adapting the emoji placement to feel
natural in each language (emoji can stay at the start of the message in all locales).

---

## 2. Share the Macaco icon as an image (WhatsApp visual card)

**Problem:** Text-only shares look bland. WhatsApp and iMessage render an image + caption
beautifully when the intent provides both `EXTRA_STREAM` (image) and `EXTRA_TEXT` (caption).

**Fix:** At share time, draw the launcher icon onto a tinted square bitmap, save it as a temp
PNG in `cacheDir`, expose it via FileProvider, and pass it as `EXTRA_STREAM`. The share text
becomes the caption.

### 2a. Add cache-path to FileProvider config

**File:** `res/xml/file_paths.xml`

Add `<cache-path>` so FileProvider can serve files from `cacheDir`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- Exposes app-internal storage (filesDir) so entry photos can be shared via FileProvider. -->
    <files-path name="internal_images" path="." />
    <!-- Exposes cache dir for temporary share images. -->
    <cache-path name="cache" path="." />
</paths>
```

### 2b. Update `shareApp()` in AppActions

**File:** `util/AppActions.kt`

Replace the current `shareApp()` implementation:

```kotlin
/**
 * Opens the system share sheet with a branded Macaco image card and a personalised caption
 * (entry count). The image is the launcher icon on a teal background — visible in WhatsApp
 * and iMessage as a photo, with the text as the caption.
 */
fun shareApp(context: Context, entryCount: Int) {
    val blurb = context.resources.getQuantityString(R.plurals.share_app_text, entryCount, entryCount)
    val shareText = "$blurb\n\n$LISTING_URL"

    // Build a 512×512 branded share card: teal background + launcher foreground icon.
    val iconUri = runCatching { buildShareIconUri(context) }.getOrNull()

    val intent = if (iconUri != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, iconUri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_app_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        // Fallback: text-only if image creation fails (e.g., low storage).
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_app_subject))
        }
    }

    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_app_chooser))
    )
}

/**
 * Draws the app's launcher foreground icon on a dark teal background, saves it to [cacheDir]
 * as `macaco_share.png`, and returns a FileProvider URI the share intent can use.
 *
 * Uses the same teal as the splash screen (SplashTealEdge = #042830) to stay on-brand.
 */
private fun buildShareIconUri(context: Context): android.net.Uri {
    val size = 512
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Teal brand background — matches SplashTealEdge (#042830).
    canvas.drawColor(android.graphics.Color.parseColor("#042830"))

    // Draw launcher foreground (the macaco monkey) centred on the canvas.
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
    drawable?.setBounds(0, 0, size, size)
    drawable?.draw(canvas)

    // Save to cacheDir — covered by <cache-path> in file_paths.xml.
    val file = java.io.File(context.cacheDir, "macaco_share.png")
    java.io.FileOutputStream(file).use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
    }
    bitmap.recycle()

    val authority = "${context.packageName}.fileprovider"
    return androidx.core.content.FileProvider.getUriForFile(context, authority, file)
}
```

Add imports if missing:
```kotlin
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add monkey + travel emoji to share text; update subject line | `res/values/strings.xml` ×11 |
| 2 | Add `<cache-path>` to FileProvider config | `res/xml/file_paths.xml` |
| 3 | Replace `shareApp()` to share branded image card + caption; add `buildShareIconUri()` helper | `util/AppActions.kt` |
