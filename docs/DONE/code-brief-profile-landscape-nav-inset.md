# Macaco — Profile: Right Pane Clears System Navigation Bar in Landscape

In landscape mode, the right pane of the Profile screen (`Box` at ~line 513) uses only
`padding(horizontal = 20.dp)`. On devices with a side navigation bar in landscape (gesture
bar or button bar on the right edge), the stats card, Subscription/Sign Out buttons, and
Delete Account link all render partly behind that bar.

Fix: add `windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))` before
the `padding(horizontal = 20.dp)` so the right pane's content starts inboard of the system bar.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Change — Right pane Box modifier (~line 513)

### BEFORE
```kotlin
            // RIGHT PANE — Trips stat + side-by-side actions (v3)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp)
```

### AFTER
```kotlin
            // RIGHT PANE — Trips stat + side-by-side actions (v3)
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))
                    .padding(horizontal = 20.dp)
```

This is a one-line insertion. The `windowInsetsPadding` must come BEFORE `padding` so the
inset pushes the content away from the bar first, then the 20 dp visual margin is added on
top of it. Swapping the order would reduce the visual margin on gesture-bar devices.

`WindowInsets` and `WindowInsetsSides` are already imported in `ProfileScreen.kt`.

---

## Why only the End side

The inset only applies to `WindowInsetsSides.End` (right in LTR locales). Applying the full
nav-bar inset (all four sides) would also add unnecessary top/bottom padding on devices with
bottom navigation in landscape. Scoping to End keeps the pane tight on the vertical axis and
only pushes it away from the side nav bar.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.End))` to right pane Box | `ProfileScreen.kt` |
