# Macaco — Journal List: Primary Tint for On This Day and Trip Headers

Two elements in `JournalListScreen.kt` use `primaryContainer` as their background colour.
In light mode, M3 generates `primaryContainer` as a very light, washed-out tint of the primary
colour (e.g. Forest = `#A8F5C8`, barely visible mint). The theme selector previews the `primary`
colour itself (`#1A6B4A` for Forest), so light mode feels completely disconnected from the chosen
theme. Change both elements to use `primary.copy(alpha = 0.12f)` — a soft tint of the actual
primary colour that looks consistent with the theme selector swatch in both light and dark mode.

This fix applies to all 7 themes automatically (no theme-specific code).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 1 — OnThisDayBanner card background (~line 1194)

**Problem:** `containerColor = primaryContainer` produces a very pale mint green in Forest light
mode, visually unrelated to the selected theme colour.

**Fix:** Replace `primaryContainer` with `primary.copy(alpha = 0.12f)` so the card shows a
soft tint of the actual primary colour. The text colour (`onPrimaryContainer`) continues to
read correctly on top of this lighter background.

```kotlin
// BEFORE
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),

// AFTER
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
```

---

## Change 2 — TripHeader row background (~line 1347)

**Problem:** `.background(primaryContainer)` has the same issue — the trip section headers
(e.g. "Berlin", "South America") appear in the washed-out container colour instead of the
theme's actual green.

**Fix:** Same substitution — `primary.copy(alpha = 0.12f)`.

```kotlin
// BEFORE
.background(MaterialTheme.colorScheme.primaryContainer)

// AFTER
.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
```

---

## Why this works across all themes and modes

`primary.copy(alpha = 0.12f)` composites the selected theme's primary colour at 12% opacity
over whatever surface lies beneath. In light mode this produces a clearly-themed but subtle
tint (not the M3-generated pale container); in dark mode it produces a similarly subtle tint
over the dark background. Both look proportionally like "a hint of the chosen colour," matching
the visual intent of the theme selector swatch.

No other uses of `primaryContainer` in `JournalListScreen.kt` are changed (lines 273 and 341
are unrelated elements outside the journal list items).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `OnThisDayBanner` card: `primaryContainer` → `primary.copy(alpha = 0.12f)` | `JournalListScreen.kt` |
| 2 | `TripHeader` row background: `primaryContainer` → `primary.copy(alpha = 0.12f)` | `JournalListScreen.kt` |
