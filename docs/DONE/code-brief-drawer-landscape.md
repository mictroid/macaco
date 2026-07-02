# Macaco — JournalListScreen: Drawer Landscape Sign-Out Visibility

Fixes the drawer in phone landscape so Sign Out (and Help if needed) are visible without
scrolling. The vc48 compact-header fix reduced the header height, but `Spacer(weight(1f))`
still pushes Sign Out all the way to the bottom edge, clipping it off-screen.
Touches one file: `ui/screens/JournalListScreen.kt`.

---

## The problem

`ModalDrawerSheet` is a `Column`. The drawer structure is:

```
[compact header ~50dp]
Spacer(8dp)
Settings          ~48dp
─ divider ─       ~17dp
Dark Mode         ~48dp
Share App         ~48dp
Rate Us           ~48dp
Help              ~48dp
Spacer(weight 1f) ← expands to fill ALL remaining space
─ divider ─       ~17dp
Sign Out          ~48dp
```

On the A53 in landscape (screen height ≈ 360dp):
- Items above the weight spacer total ≈ 315dp
- Remaining space = 360 − 315 = 45dp
- Divider + Sign Out = 65dp → Sign Out overflows by ~20dp and is clipped

`Spacer(Modifier.weight(1f))` makes sense in portrait (pinning Sign Out to the bottom of a
tall drawer). In landscape it's the cause of the overflow.

---

## Fix: Swap weight spacer for a fixed spacer in landscape

`drawerIsLandscape` is already declared at the top of the `ModalDrawerSheet` content block
(line 234). Use it at the spacer site to disable the weight behaviour in landscape.

```kotlin
// BEFORE (JournalListScreen.kt ~line 448)
Spacer(Modifier.weight(1f))

// AFTER
if (drawerIsLandscape) {
    Spacer(Modifier.height(8.dp))
} else {
    Spacer(Modifier.weight(1f))
}
```

In landscape Sign Out now sits directly below Help (separated only by the existing
`HorizontalDivider` + the 8dp spacer), so the full item list is visible without scrolling.

In portrait nothing changes — the weight spacer still pushes Sign Out to the drawer bottom.

---

## Fix 2: Also tighten divider padding in landscape (if still tight after Fix 1)

If Fix 1 alone isn't quite enough on a very short landscape (e.g., a device with tall status
bar insets), also halve the vertical padding on the two `HorizontalDivider`s while in
landscape. The two dividers together save ≈ 16dp.

```kotlin
// BEFORE — both HorizontalDivider calls (lines ~394 and ~450)
HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

// AFTER — conditional vertical padding
HorizontalDivider(
    modifier = Modifier.padding(
        horizontal = 16.dp,
        vertical = if (drawerIsLandscape) 4.dp else 8.dp
    )
)
```

Apply to **both** `HorizontalDivider` calls (one above Dark Mode, one above Sign Out).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace `Spacer(weight(1f))` with `Spacer(8dp)` in landscape so Sign Out isn't pushed off-screen | `JournalListScreen.kt` |
| 2 | Tighten both `HorizontalDivider` vertical padding 8dp → 4dp in landscape | `JournalListScreen.kt` |
