# Macaco — NewEditEntryScreen: Improve HintRow readability over the macaco watermark

The `HintRow` composable (used for "Add photos to bring your memory to life", "Try #beach, #food,
#family", "Tap the mic to speak your memory") uses `primary.copy(alpha = 0.6f)` as its text and
icon color. Over the tiled macaco-icon watermark the semi-transparent primary color blends into the
background and becomes barely readable.

---

## Fix

**File:** `ui/screens/NewEditEntryScreen.kt`

Find `private fun HintRow` (currently around line 860) and replace its implementation:

**Current:**
```kotlin
@Composable
private fun HintRow(icon: ImageVector?, text: String) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
```

**Replace with:**
```kotlin
@Composable
private fun HintRow(icon: ImageVector?, text: String) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 6.dp, start = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
```

Two changes:
- Color switches from `primary.copy(alpha = 0.6f)` to `onSurfaceVariant` — designed for readable
  secondary text on any surface, full opacity.
- A semi-transparent surface scrim pill (`surface` at 75% alpha, clipped to 6dp rounded corners)
  sits behind the text, lifting it off the watermark on all themes without looking heavy.

---

## Required imports (add if missing)

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
```

`RoundedCornerShape` and `clip` are likely already imported for other elements in this file —
check before adding.

---

## Summary

| File | Change |
|------|--------|
| `ui/screens/NewEditEntryScreen.kt` | Replace `HintRow` body: use `onSurfaceVariant` color + semi-transparent surface pill background for readability over the macaco watermark |
