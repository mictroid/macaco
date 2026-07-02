# Macaco — ProfileScreen: 2-pane for tablets + left pane padding reduction

One file: `ui/screens/ProfileScreen.kt`.

Read `docs/DONE/code-brief-profile-landscape.md` for prior context (2-pane layout
introduced). This brief has two fixes: expanding 2-pane activation to tablets, and
reducing the top padding in the left pane so the identity info is fully visible.

---

## Fix 1 — Expand 2-pane to all wide screens (tablets)

**Problem:** The 2-pane landscape layout is gated on:
```kotlin
val isLandscape = configuration.screenHeightDp < 480
```
This condition evaluates to `false` on tablets (Samsung Tab A9+ landscape height ≈ 750dp),
so tablets always use the portrait single-column layout. Profile should use 2 columns on
ANY wide screen — both phones in landscape AND tablets in any orientation.

**Fix:** Add `|| configuration.screenWidthDp >= 600` to the condition. 600dp is the
standard Material 3 breakpoint for compact→medium window size class (Android's definition
of a tablet-class width).

```kotlin
// BEFORE (line 104 in ProfileScreen.kt)
val isLandscape = configuration.screenHeightDp < 480

// AFTER — also activates on any screen ≥ 600dp wide (tablets)
val isLandscape = configuration.screenHeightDp < 480 || configuration.screenWidthDp >= 600
```

No other changes — the existing 2-pane `Row` layout is already correct; it just wasn't
being reached on tablets.

**File:** `ui/screens/ProfileScreen.kt`

---

## Fix 2 — Reduce left pane top padding

**Problem:** In the 2-pane layout the left Column has:
- The compact banner Box (height ≈ 44dp including status bar padding)
- `Spacer(Modifier.height(16.dp))`
- 88dp avatar outer Box

On a phone in landscape (screen height ≈ 360dp), 44 + 16 + 88 = 148dp is consumed before
the user's name appears, leaving only ~212dp for name, email, Google Account chip, stats,
and member-since text. On typical A53 landscape font sizes, the last 1–2 rows fall below
the visible area even with `verticalScroll`.

On a tablet (where Fix 1 now enables 2-pane), the same spacer is simply wasted whitespace.

**Fix:** Reduce the post-banner spacer from 16dp to 8dp.

```kotlin
// BEFORE (line ~318 in ProfileScreen.kt, inside the landscape left Column)
if (user != null) {
    Spacer(Modifier.height(16.dp))

    // Avatar — slightly smaller in landscape
    Box(
        modifier = Modifier
            .size(88.dp)
            ...

// AFTER — 8dp saves meaningful vertical space on phones; unnoticeable on tablets
if (user != null) {
    Spacer(Modifier.height(8.dp))

    Box(
        modifier = Modifier
            .size(88.dp)
            ...
```

**File:** `ui/screens/ProfileScreen.kt`

---

## Scope

- **In:** `isLandscape` condition (now includes tablet widths ≥ 600dp); top spacer 16→8dp.
- **Out:** Right pane — user confirmed it looks good, unchanged.
- **Out:** Avatar size (88dp) — unchanged.
- **Out:** Portrait layout — unchanged.
- **Out:** Any other screen; the condition change only affects ProfileScreen.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `isLandscape` adds `|| configuration.screenWidthDp >= 600` | `ProfileScreen.kt` |
| 2 | Left pane top spacer: `height(16.dp)` → `height(8.dp)` | `ProfileScreen.kt` |
