# Macaco — Entry Detail: Fix Off-Center Brand Logo in Header

The "macaco" monkey icon in `EntryDetailScreen.kt`'s top header sits visibly left of center, in
both portrait and landscape (confirmed via phone-portrait and tablet-landscape screenshots — the
icon drifts further off-center on the wider tablet layout). Root cause is a layout bug, not a
missing size/style fix like the prior `code-brief-macaco-brand-header-consistency.md` round (that
brief explicitly scoped `EntryDetailScreen.kt` out — "not mentioned in the screenshots and has a
different purpose" — this is that follow-up). Touches 1 file, 1 change.

---

## The off-center logo

**Problem:** The header is a single `Row` with the back button + page counter ("2 / 10") on the
left, the brand icon in the middle, and three action icons (share / edit / delete) on the right —
separated by two `Spacer(Modifier.weight(1f))`:

```kotlin
Row(...) {
    IconButton(onBack) { ... }              // ~40dp
    if (entries.size > 1) { Text("2 / 10") } // variable width, ~30-50dp
    Spacer(Modifier.weight(1f))
    Column(...) { Image(logo) }              // centered "in the leftover space"
    Spacer(Modifier.weight(1f))
    IconButton(share) { ... }                // 40dp
    IconButton(edit) { ... }                 // 40dp
    IconButton(delete) { ... }               // 40dp
}
```

Two equal-weight spacers only center the middle content when the **fixed-width content on each
side is equal**. Here it isn't: the left side is back-button + counter (~70-90dp), the right side
is three 40dp icon buttons (120dp). Since the right side is wider, the weighted-spacer math pulls
the "centered" logo toward the narrower (left) side — it's mathematically guaranteed to drift
left whenever `leftWidth != rightWidth`, which is exactly what the screenshots show. This gets
worse on wider screens (tablet landscape) because the fixed offset scales with the available
slack space.

**Fix:** Replace the `Row` + two weighted `Spacer`s with a `Box` containing three independently
`Modifier.align`-ed children: leading cluster pinned to `CenterStart`, brand logo pinned to
`Alignment.Center` (the box's true center, independent of sibling widths), trailing cluster
pinned to `CenterEnd`. This is a standard Compose pattern for "fixed-width start/end content,
guaranteed-centered middle content" and has no dependency on the left/right clusters being equal
width.

```
BEFORE (weighted spacers — logo drifts toward the narrower side):
┌──────┬────────┬═══════════╗  logo  ╔═══════════┬──────┬──────┬──────┐
│ back │ 2 / 10 │  spacer(1) ║ LOGO   ║ spacer(1) │share │ edit │delete│
└──────┴────────┴═══════════╝        ╚═══════════┴──────┴──────┴──────┘
   ~80dp                                                    120dp
   (logo center = leftWidth + remaining/2, which is left of true center when leftWidth < rightWidth)

AFTER (Box + align — logo pinned to true center regardless of side widths):
┌──────────────────────────────────────────────────────────────────────┐
│ ┌──────┬────────┐                 [ LOGO ]              ┌──────┬──────┬──────┐ │
│ │ back │ 2 / 10 │            (Alignment.Center)          │share │ edit │delete│ │
│ └──────┴────────┘  CenterStart                CenterEnd └──────┴──────┴──────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### BEFORE — `EntryDetailScreen.kt` (~lines 295–361, inside `topBar = { Box(...) { ... } }`)

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.common_back),
            tint = Color.White
        )
    }
    // Orientation among entries — only worth showing once there's more than one.
    if (entries.size > 1) {
        AnimatedContent(
            targetState = entriesPagerState.currentPage + 1,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "entryCounter"
        ) { pageNumber ->
            Text(
                text = "$pageNumber / ${entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = SplashGold,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
    Spacer(Modifier.weight(1f))
    // Brand block, centred in the leftover space. Icon stacked above the
    // wordmark (matches Journal/Map/Help & About's compact-header convention —
    // this used to sit inline, the odd one out). Wordmark drops on narrow
    // screens so it can't collide with the action cluster.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        if (LocalConfiguration.current.screenWidthDp >= 420) {
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp
            )
        }
    }
    Spacer(Modifier.weight(1f))
    IconButton(onClick = { shareEntry(context, currentEntry, cachedDrivePhotos) }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.entry_detail_share_cd), tint = Color.White)
    }
    IconButton(onClick = { onEdit(currentEntry.id) }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.entry_detail_edit_cd), tint = Color.White)
    }
    IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.entry_detail_delete_cd),
            tint = Color.White
        )
    }
}
```

### AFTER

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp)
) {
    // Leading cluster: back button + page counter, pinned to the start.
    Row(
        modifier = Modifier.align(Alignment.CenterStart),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White
            )
        }
        // Orientation among entries — only worth showing once there's more than one.
        if (entries.size > 1) {
            AnimatedContent(
                targetState = entriesPagerState.currentPage + 1,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "entryCounter"
            ) { pageNumber ->
                Text(
                    text = "$pageNumber / ${entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = SplashGold,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
    // Brand block, pinned to the true center of the header. Previously this sat
    // in the leftover space between two weighted spacers, which only centers it
    // when the leading (back+counter) and trailing (3 icons) clusters are the
    // same width — they aren't, so the logo drifted left. Icon stacked above the
    // wordmark (matches Journal/Map/Help & About's compact-header convention).
    // Wordmark drops on narrow screens so it can't collide with the action cluster.
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        if (LocalConfiguration.current.screenWidthDp >= 420) {
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp
            )
        }
    }
    // Trailing cluster: share/edit/delete, pinned to the end.
    Row(
        modifier = Modifier.align(Alignment.CenterEnd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { shareEntry(context, currentEntry, cachedDrivePhotos) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.entry_detail_share_cd), tint = Color.White)
        }
        IconButton(onClick = { onEdit(currentEntry.id) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.entry_detail_edit_cd), tint = Color.White)
        }
        IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.entry_detail_delete_cd),
                tint = Color.White
            )
        }
    }
}
```

No new imports needed — `Box`, `Row`, `Column`, and `Alignment` are all already imported in this
file (used elsewhere, e.g. the outer `Box(modifier = Modifier.fillMaxSize())` a few lines above
this block).

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/EntryDetailScreen.kt`

---

## Verify after implementing

- Phone portrait: logo should sit at the exact horizontal midpoint of the header, not shifted
  toward the back button.
- Tablet/phone landscape: same check — the drift was more visible here in the screenshots because
  the header is wider, so this is the more important orientation to re-check.
- Multi-photo entries (counter text visible, e.g. "2 / 10") vs. single-photo entries (no counter,
  `entries.size == 1`) — both should still center the logo correctly, since the fix doesn't depend
  on the counter's width.

## Out of scope

- Not touching `MacacoBrandBlock.kt` or any of the 5 screens it already covers
  (Journal/Adventures/Profile/Settings/Help & About) — those were fixed in
  `docs/DONE/code-brief-macaco-brand-header-consistency.md` and are unaffected by this bug (their
  headers don't have asymmetric leading/trailing clusters).
- Not folding `EntryDetailScreen.kt`'s header into `MacacoBrandBlock` — that composable's API only
  supports one trailing slot *inside* the icon/wordmark column, not independent leading and
  trailing clusters outside it. Doing so would require reshaping `MacacoBrandBlock`'s API and is a
  bigger refactor than this bug fix warrants; flag back if this is not the desired approach.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Replace weighted-spacer `Row` with `Box` + `Alignment`-pinned clusters so the brand logo is truly centered regardless of left/right cluster width | `EntryDetailScreen.kt` |
