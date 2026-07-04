# Macaco — Profile: Single Column on All Tablets

The Profile screen shows a two-pane layout (identity left, stats+actions right) when
`isLandscape` is true. The condition currently includes tablet landscape, which looks messy on
large screens. Change it so **only phones in landscape** get the two-pane layout — tablets
always use the single-column portrait layout regardless of orientation.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Change — Narrow the `isLandscape` condition (~line 113)

### BEFORE
```kotlin
// 2-pane only when actually wide-and-short: phones in landscape (short height) OR tablets
// (≥600dp wide) but ONLY in landscape (width > height). Tablet PORTRAIT falls through to the
// single-column layout, which flows better tall-and-narrow. (v3)
val isLandscape = configuration.screenHeightDp < 480 ||
    (configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp)
```

### AFTER
```kotlin
// 2-pane only on phones in landscape (short screen). Tablets always use the single-column
// portrait layout — the two-pane layout looked cramped on large screens.
val isLandscape = configuration.screenHeightDp < 480
```

No other changes. The rest of the `if (isLandscape)` / `else` branching is untouched — tablets
now always fall through to the `else` (portrait) branch.
