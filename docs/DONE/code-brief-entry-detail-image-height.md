# Macaco — Entry Detail: Hero Photo Height on Tablet

The photo collage on `EntryDetailScreen` is hardcoded to `260.dp`. On a tablet (e.g. Samsung Tab
A9+, ~800–900dp screen height) that is only ~30% of the screen, leaving a large empty gap below
tags and description. The fix scales the hero height to ≥50% of the screen on tablet-class screens
(sw600dp+), leaving phones unchanged.

---

## 1. Add `LocalConfiguration` import and compute `heroHeight`

**Problem:** Both the single-photo and multi-photo cases use a hardcoded `260.dp`. On tablets this
produces a cramped hero with a lot of dead whitespace below.

**Fix:** At the top of the entry rendering block — just before the `when { photoCount == 0 → ... }`
branch — read the screen height via `LocalConfiguration` and pick a height: 50% of screen height
on tablet, or 260dp on phone.

```kotlin
// BEFORE — EntryDetailScreen.kt (line 293, just before `val photoCount = ...`)
                val photoCount = maxOf(entry.photoUris.size, entry.driveFileIds.size)

// AFTER — insert two lines above photoCount
                val configuration = LocalConfiguration.current
                val heroHeight = if (configuration.screenWidthDp >= 600) {
                    (configuration.screenHeightDp * 0.52f).dp
                } else {
                    260.dp
                }
                val photoCount = maxOf(entry.photoUris.size, entry.driveFileIds.size)
```

`screenWidthDp >= 600` is the standard Compose/Material 3 tablet breakpoint.
`0.52f` gives ~52% of screen height, so even short tablet screens hit at least ~400dp.

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 2. Use `heroHeight` in the single-photo case

```kotlin
// BEFORE — line 324
                    photoCount == 1 -> JournalPhoto(
                        data = entry.displayPhotoUri(0, cachedDrivePhotos),
                        onClick = { galleryStartIndex = 0 },
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )

// AFTER
                    photoCount == 1 -> JournalPhoto(
                        data = entry.displayPhotoUri(0, cachedDrivePhotos),
                        onClick = { galleryStartIndex = 0 },
                        modifier = Modifier.fillMaxWidth().height(heroHeight)
                    )
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 3. Use `heroHeight` in the multi-photo collage Row

```kotlin
// BEFORE — line 338
                            Row(
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {

// AFTER
                            Row(
                                modifier = Modifier.fillMaxWidth().height(heroHeight),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## 4. Add `LocalConfiguration` import

```kotlin
// BEFORE — EntryDetailScreen.kt imports (near line 54, after LocalContext)
import androidx.compose.ui.platform.LocalContext

// AFTER — add below LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
```

**File:** `ui/screens/EntryDetailScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Import `LocalConfiguration` | `ui/screens/EntryDetailScreen.kt` |
| 2 | Compute `heroHeight` = 52% screen on tablet, 260dp on phone | `ui/screens/EntryDetailScreen.kt` |
| 3 | Replace `height(260.dp)` → `height(heroHeight)` in single-photo case | `ui/screens/EntryDetailScreen.kt` |
| 4 | Replace `height(260.dp)` → `height(heroHeight)` in multi-photo Row | `ui/screens/EntryDetailScreen.kt` |

No string resources, no ViewModel changes, no other files.
