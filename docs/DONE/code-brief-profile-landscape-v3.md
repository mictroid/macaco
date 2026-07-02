# Macaco — ProfileScreen: Landscape Layout v3

Redesigns the landscape two-pane profile layout so the branded header spans the full screen
width, the right pane shows Trips stat + side-by-side action buttons, and the left pane
stays focused on identity info. Also fixes the 2-pane condition so it no longer fires on
tablets in PORTRAIT orientation (portrait tablets fall through to the standard single-column
layout). Supersedes `docs/DONE/code-brief-profile-landscape-v2.md`.
Touches one file: `ui/screens/ProfileScreen.kt`.

---

## Fix 0: Correct the `isLandscape` condition (do not 2-pane on tablet portrait)

**Problem:** The current condition is:
```kotlin
val isLandscape = configuration.screenHeightDp < 480 || configuration.screenWidthDp >= 600
```
`screenWidthDp >= 600` is true for tablets in BOTH portrait AND landscape. This means the
2-pane layout activates on tablet portrait — a tall-and-narrow layout where a single column
flows naturally and the 2-pane just creates a cramped right column with lots of empty space.

**Fix:** Gate the tablet 2-pane on width > height (i.e., actually landscape):
```kotlin
// BEFORE (ProfileScreen.kt ~line 106)
val isLandscape = configuration.screenHeightDp < 480 || configuration.screenWidthDp >= 600

// AFTER
val isLandscape = configuration.screenHeightDp < 480 ||
    (configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp)
```

This gives the correct behaviour:
- Phone portrait → single column ✓
- Phone landscape (height < 480) → 2-pane ✓
- Tablet portrait (width ≥ 600 but height > width) → single column ✓
- Tablet landscape (width ≥ 600 AND width > height) → 2-pane ✓

The single-column portrait layout already exists and looks fine on tablet — centred content,
no scrolling needed. No other changes required for tablet portrait.

---

## Current landscape layout (post-v2)

```
┌──── LEFT (0.5f) ────┬──── RIGHT (0.5f) ────┐
│ [teal header        │                       │
│  ← macaco]          │  [Subscribe]          │
│ avatar              │  [Sign Out]           │
│ name                │  [Delete Account]     │
│ email               │                       │
│ Google chip         │                       │
│ [4 stats — cut off] │                       │
└─────────────────────┴───────────────────────┘
```

**Problems (landscape 2-pane only — tablet portrait now uses single column after Fix 0):**
- Header only spans the left 50% of the screen
- Entries/Trips stats are cut off (too wide for the padded column)
- Right pane is sparse — Trips stat missing, buttons stacked vertically waste space
- No Macaco icon in the header (removed in v2 for compactness)

---

## Fix 1: Move header outside the Row (full width)

**Problem:** The branded `Box` header sits inside the left `Column` (which has `weight(0.5f)`),
so it only covers half the screen.

**Fix:** Extract the header to a full-width `Box` that wraps the entire landscape layout in a
`Column`, with the two-pane `Row` below it.

```
┌────────────── FULL WIDTH ──────────────────┐
│  ← (back)    🐒 macaco            [avatar] │  ← thin, ~44dp total
├──── LEFT (0.5f) ──────┬─ RIGHT (0.5f) ─────┤
│  name                 │  [Subscribe] [Sign] │
│  email                │  ── divider ──      │
│  [Google chip]        │  ✈ x Trips         │
│  [4 stats card]       │  [Delete Account]   │
└───────────────────────┴─────────────────────┘
```

**ASCII — new structure:**
```
Column(fillMaxSize) {
  Box(fullWidth, teal bg, statusBarsPadding)   ← header row
  Row(weight(1f)) {
    Column(weight(0.5f), scrollable)           ← identity left pane
    Column(weight(0.5f), centered)             ← actions right pane
  }
}
```

```kotlin
// BEFORE — inside the `if (isLandscape)` branch (ProfileScreen.kt ~line 272)
// The branded header is INSIDE the left Column:
Row(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
) {
    // LEFT PANE — compact banner + identity info (scrollable)
    Column(
        modifier = Modifier
            .weight(0.5f)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact banner — back arrow left, "macaco" centred, no tagline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .statusBarsPadding()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 5.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 12.dp)
            )
        }

        val user = currentUser
        if (user != null) {
            Spacer(Modifier.height(8.dp))
            // … avatar, name, email, chip, stats …

// AFTER — wrap in a Column; header is now OUTSIDE the left pane
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
) {
    // ── Full-width compact header ──────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
    ) {
        // Back arrow
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White
            )
        }
        // Macaco brand text (centred)
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(20.dp).offset(y = (-2).dp),
                colorFilter = ColorFilter.tint(SplashGoldBright)
            )
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
        }
        // Avatar thumbnail pinned to end (tappable shortcut)
        val displayModel: Any? = profilePhotoUri ?: currentUser?.photoUrl
        if (displayModel != null) {
            AsyncImage(
                model = displayModel,
                contentDescription = stringResource(R.string.profile_photo_cd),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { showPhotoSourceSheet = true },
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.Person)
            )
        }
    }

    // ── Two-pane Row ──────────────────────────────────────────────────
    Row(modifier = Modifier.fillMaxSize().weight(1f)) {

        // LEFT PANE — scrollable identity info (no header inside)
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val user = currentUser
            if (user != null) {
                Spacer(Modifier.height(8.dp))
                // … rest of left pane unchanged (avatar, name, email, chip, stats card, memberSince) …
```

**Imports to add if missing:**
```kotlin
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
```

---

## Fix 2: Right pane — side-by-side buttons + Trips stat

**Problem:** Subscribe and Sign Out are stacked vertically and there's no Trips stat. The pane
feels empty and wastes horizontal space.

**Fix:** Show Trips stat at the top of the right pane (pulled from the `tripCount` calculation
already done in the left pane — hoist it above the `if (user != null)` block so both panes can
read it). Put Subscribe and Sign Out in a horizontal Row. Keep Delete Account as a smaller
TextButton below.

```
RIGHT PANE (0.5f):
┌────────────────────────┐
│                        │
│     ✈  3 Trips         │  ← stat centred
│                        │
│  [Subscribe] [Sign Out]│  ← side-by-side OutlinedButtons
│                        │
│  [Delete Account]      │  ← TextButton, smaller
└────────────────────────┘
```

```kotlin
// BEFORE — right pane (ProfileScreen.kt ~line 503)
// RIGHT PANE — action buttons centred vertically, macaco logo pinned to bottom
Box(
    modifier = Modifier
        .weight(0.5f)
        .fillMaxHeight()
        .padding(horizontal = 24.dp)
) {
    if (currentUser != null) {
        Column(
            modifier = Modifier.align(Alignment.Center),

// AFTER — right pane with Trips stat + side-by-side buttons
Column(
    modifier = Modifier
        .weight(0.5f)
        .fillMaxHeight()
        .padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
) {
    if (currentUser != null) {
        // Trips stat (if any trips exist)
        if (tripCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    Icons.Outlined.Flight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "$tripCount ${stringResource(R.string.profile_trips)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Subscribe + Sign Out side-by-side
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onSubscribe,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.profile_subscribe),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.profile_sign_out),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Delete Account
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showDeleteAccountDialog = true }) {
            Text(
                stringResource(R.string.profile_delete_account),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

**Note on `tripCount`:** It's currently computed inline in the left pane column. Hoist it to
before the `if (isLandscape)` branch so both panes can read it:

```kotlin
// Hoist ABOVE the isLandscape branch, alongside currentUser / entries reads:
val tripCount = entries
    .mapNotNull { it.tripName?.trim()?.ifBlank { null } }
    .distinct()
    .size
```

Then remove the duplicate `val tripCount` inside the left pane's `if (user != null)` block.

**Icon import:**
```kotlin
import androidx.compose.material.icons.outlined.Flight
```

---

## Fix 3: Left pane stats card — remove horizontal padding so stats fit

**Problem:** The stats card has `.padding(horizontal = 16.dp)` inside a column that's already
`weight(0.5f)` — with the outer padding too, "Entries | Trips | Locations | Photos" gets clipped.

**Fix:** Remove the horizontal padding from the stats `Card` modifier. The column's
`horizontalAlignment = CenterHorizontally` already keeps it centred.

```kotlin
// BEFORE — stats Card in left pane (ProfileScreen.kt ~line 417)
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),

// AFTER
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
```

---

## Summary

| # | Change | File |
|---|--------|------|
| 0 | Fix `isLandscape` condition — tablet portrait falls through to single column | `ProfileScreen.kt` |
| 1 | Full-width header — move out of left pane into wrapping Column (landscape only) | `ProfileScreen.kt` |
| 2 | Right pane — side-by-side Subscribe+SignOut, Trips stat, hoisted tripCount | `ProfileScreen.kt` |
| 3 | Stats card — reduce horizontal padding 16dp → 8dp | `ProfileScreen.kt` |
