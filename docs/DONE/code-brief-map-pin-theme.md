# Macaco — MapScreen: Theme-Aware Pin Markers

Replaces the hardcoded teal `#1B96B3` map pin with a marker that uses the current Material 3
theme's primary colour, matching the theme the user has selected. Touches one file:
`ui/screens/MapScreen.kt`.

---

## Fix: Parameterise marker colour from theme primary

**Problem:** `createTealMarkerBitmap(context)` always paints the pin circle with the literal hex
colour `#1B96B3` regardless of which theme the user has chosen. Switching to Purple, Amber, or
any other theme leaves the map pins blue.

**Fix:**
1. Rename the function to `createThemedMarkerBitmap(context, colorInt)` and replace the
   hardcoded `parseColor` with the passed `colorInt`.
2. In the `MapScreen` composable, capture `MaterialTheme.colorScheme.primary.toArgb()` as a
   `val` inside the composable scope.
3. Pass it into the `onMapLoaded` callback (lambda capture).
4. Add a `LaunchedEffect(primaryColorArgb, mapLoaded)` so the marker is recreated if the user
   changes theme while the map is open.

---

### Step 1 — Rename and parameterise the function

```kotlin
// BEFORE (MapScreen.kt ~line 104)
private fun createTealMarkerBitmap(context: Context): Bitmap {
    val dp = context.resources.displayMetrics.density
    val size = (36 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f

    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#1B96B3")
    canvas.drawCircle(radius, radius, radius - 2 * dp, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3 * dp
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius - 3.5f * dp, paint)

    return bitmap
}

// AFTER
private fun createThemedMarkerBitmap(context: Context, colorInt: Int): Bitmap {
    val dp = context.resources.displayMetrics.density
    val size = (36 * dp).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f

    paint.style = Paint.Style.FILL
    paint.color = colorInt
    canvas.drawCircle(radius, radius, radius - 2 * dp, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3 * dp
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius - 3.5f * dp, paint)

    return bitmap
}
```

---

### Step 2 — Capture primary colour in composable scope

```kotlin
// BEFORE (MapScreen.kt ~line 139, inside the MapScreen composable)
var tealMarker by remember { mutableStateOf<BitmapDescriptor?>(null) }

// AFTER — rename the state var and add the colour capture
var themedMarker by remember { mutableStateOf<BitmapDescriptor?>(null) }
val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
```

**Import to add if missing:**
```kotlin
import androidx.compose.ui.graphics.toArgb
```

---

### Step 3 — Pass colour into onMapLoaded

```kotlin
// BEFORE (MapScreen.kt ~line 507)
onMapLoaded = {
    mapLoaded = true
    tealMarker = BitmapDescriptorFactory.fromBitmap(createTealMarkerBitmap(context))
},

// AFTER
onMapLoaded = {
    mapLoaded = true
    themedMarker = BitmapDescriptorFactory.fromBitmap(
        createThemedMarkerBitmap(context, primaryColorArgb)
    )
},
```

---

### Step 4 — Recreate marker when theme changes

Add this `LaunchedEffect` after the existing state declarations (near the `var themedMarker`
line), so switching theme while the map screen is open refreshes the pins:

```kotlin
// Add below `val primaryColorArgb = ...`
LaunchedEffect(primaryColorArgb, mapLoaded) {
    if (mapLoaded) {
        themedMarker = BitmapDescriptorFactory.fromBitmap(
            createThemedMarkerBitmap(context, primaryColorArgb)
        )
    }
}
```

---

### Step 5 — Update usage of the state variable

Rename all references to `tealMarker` → `themedMarker` in the `GoogleMap` composable block:

```kotlin
// BEFORE (MapScreen.kt ~line 516)
val marker = tealMarker
if (marker != null) {

// AFTER
val marker = themedMarker
if (marker != null) {
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Rename `createTealMarkerBitmap` → `createThemedMarkerBitmap(context, colorInt)` | `MapScreen.kt` |
| 2 | Capture `primaryColorArgb` from `MaterialTheme.colorScheme.primary.toArgb()` in composable scope | `MapScreen.kt` |
| 3 | Pass `primaryColorArgb` into `onMapLoaded` callback | `MapScreen.kt` |
| 4 | Add `LaunchedEffect(primaryColorArgb, mapLoaded)` to refresh marker on theme change | `MapScreen.kt` |
| 5 | Rename `tealMarker` → `themedMarker` state var throughout | `MapScreen.kt` |
