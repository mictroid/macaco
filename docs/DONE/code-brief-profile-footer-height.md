# Macaco — ProfileScreen: Reduce Footer Height to Eliminate Unnecessary Scroll

The branded footer at the bottom of ProfileScreen is 160dp tall, consuming too much vertical
space and forcing the user to scroll to see their stats on some devices. Shrinking it to 80dp
gives the scrollable content enough room to display fully without scrolling on typical phones
and tablets. Touches `ui/screens/ProfileScreen.kt` only.

---

## Change: reduce footer from 160dp to 80dp

**Problem:** The footer `Box` has `height(160.dp)`. Combined with the three action buttons
(~160dp) and the branded banner (~100dp), there is only ~320dp left for the scrollable content
area on a typical phone. This forces the user to scroll to see their stats card and "Member
since" text even though the content itself is compact. On the Tab A9+ tablet there is plenty of
vertical room but the oversized footer still wastes it.

**Fix:** Change the footer height from `160.dp` to `80.dp` and reduce the logo size inside it
from `120.dp` to `64.dp` so it remains proportional. No structural layout changes are needed —
the rest of the screen (the `weight(1f)` scrollable area + pinned buttons) adjusts automatically.

```
BEFORE                          AFTER
┌──────────────────────┐        ┌──────────────────────┐
│  Branded banner      │        │  Branded banner      │
│  Avatar + name       │        │  Avatar + name       │
│  Stats card          │◄scroll │  Stats card          │
│  Member since        │        │  Member since        │
├──────────────────────┤        ├──────────────────────┤
│  Subscription btn    │        │  Subscription btn    │
│  Sign Out btn        │        │  Sign Out btn        │
│  Delete Account      │        │  Delete Account      │
├──────────────────────┤        ├──────────────────────┤
│                      │        │  [logo 64dp]  80dp   │
│  [logo 120dp] 160dp  │        └──────────────────────┘
│                      │
└──────────────────────┘
```

Find the footer `Box` near the bottom of `ProfileScreen` (after the action buttons, just before
the closing of the outer `Column`). It currently reads:

```kotlin
// BEFORE:
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF071E26),
                    Color(0xFF0E5A6B),
                )
            )
        ),
    contentAlignment = Alignment.Center
) {
    // scrim box at top (keep as-is) ...
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(120.dp)   // ← also shrink this
    )
}

// AFTER:
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(80.dp)                      // ← 160 → 80
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF071E26),
                    Color(0xFF0E5A6B),
                )
            )
        ),
    contentAlignment = Alignment.Center
) {
    // scrim box at top (keep as-is) ...
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(64.dp)     // ← 120 → 64
    )
}
```

The scrim overlay `Box` inside the footer (`height(24.dp)`, `Alignment.TopCenter`) is unchanged.

**No new imports, no string changes, no other files.**

**File:** `ui/screens/ProfileScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Footer `Box` height 160dp → 80dp; logo `Image` size 120dp → 64dp | `ui/screens/ProfileScreen.kt` |
