# Macaco — Profile: Add Breathing Room Above the Avatar

The Profile screen's avatar circle sits flush against the teal/white seam left by the banner,
with zero additional clearance — device screenshot (2026-07-12) shows the circle almost
touching the header. Add a small fixed spacer so the avatar reads as centered in its own zone
rather than pinned to the header. File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`.

---

## Why this is safe from a scroll standpoint

The v3 padding-tightening pass (`docs/DONE/code-brief-profile-portrait-padding.md`) intentionally
compressed every spacer in this screen to make the action grid fit without scrolling on typical
phones. Re-adding vertical space anywhere in that column risks reintroducing the scroll the v3
brief eliminated. But the same screenshot shows a large empty gap between "Member since" and the
bottom nav bar — there's slack to spend. A single 14dp spacer at the top (instead of shaving it
off the bottom, which is already tight) stays well inside that slack on a typical phone. Verify
on the smallest supported screen (see Test matrix) before shipping.

---

## Change — Insert a top spacer before the avatar (signed-in branch)

**Problem:** The identity Column (`~line 361`) opens directly with the avatar `Box` — no spacer
between the banner and the avatar. The banner's own `bannerBottomPadding` (32dp in the default,
non-collapsed, non-landscape state) is the *only* clearance, and it's inside the teal banner
itself, not perceived as space belonging to the avatar. Visually the circle reads as glued to
the header.

**Fix:** Add a small fixed `Spacer` as the first child of the identity Column, before the avatar
`Box`. Keep it out of the `collapsed` / landscape states implicitly — it's a flat value, not tied
to `bannerBottomPadding`, so it won't compound with the header's own collapse animation.

```
BEFORE                          AFTER
┌─────────────────────┐         ┌─────────────────────┐
│  banner (bot 32dp)  │         │  banner (bot 32dp)  │
│  ●●●  ← avatar       │         │                      │
│  flush against seam  │         │   +14dp spacer       │
│  Michael T            │         │  ●●●  ← avatar       │
│  ...                   │         │  Michael T            │
└─────────────────────┘         └─────────────────────┘
```

### BEFORE (`~line 361`)
```kotlin
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            val user = currentUser
            if (user != null) {
                // Tappable avatar circle, with a background-colored ring so it reads over the banner.
                Box(
```

### AFTER
```kotlin
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            val user = currentUser
            if (user != null) {
                // Small fixed clearance so the avatar doesn't read as glued to the banner seam
                // (device screenshot 2026-07-12). Deliberately NOT tied to bannerBottomPadding —
                // this is perceived avatar-zone space, independent of the header's own collapse
                // animation.
                Spacer(Modifier.height(14.dp))

                // Tappable avatar circle, with a background-colored ring so it reads over the banner.
                Box(
```

**Scope note:** the signed-out branch (`else` at `~line 529`) already opens with
`Spacer(Modifier.height(56.dp))` before its content, so it already has ample top clearance —
leave it untouched.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add `Spacer(Modifier.height(14.dp))` before the avatar Box in the signed-in identity section | `ProfileScreen.kt` |

**Test matrix:** phone portrait (small screen, e.g. 360×640, to confirm no scroll is triggered),
phone portrait (typical, e.g. 412×915), tablet portrait/landscape, collapsed state (scroll down
then back up — spacer should not visually clash with the tightened collapsed banner).
