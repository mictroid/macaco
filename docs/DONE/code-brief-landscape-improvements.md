# Macaco — Landscape phone improvements

Three screens are badly broken in landscape orientation on phones. This brief fixes them.
Touches `JournalListScreen.kt`, `MapScreen.kt`, and `EntryDetailScreen.kt`.

**Landscape detection used throughout:** `LocalConfiguration.current.screenHeightDp < 480`
Phones in landscape are ~350–430 dp tall; tablets in landscape are ~750–900 dp tall.
This threshold cleanly separates them without needing `sw600dp` (which is the *smallest* width,
not the current-orientation width).

---

## Change 1 — Journal List: compact header in landscape

**Problem:** The branded header (48 dp monkey icon + "macaco" wordmark + tagline + memory count)
is a tall centred Column. In landscape it occupies ~130 dp of a ~390 dp screen, leaving only
~200 dp for tags + entries after the nav bar. Users see almost no content.

```
PORTRAIT (current — fine)          LANDSCAPE (current — broken)
┌─────────────────────────┐         ┌───────────────────────────────────────────┐
│  🐒  macaco             │         │  🐒  macaco                               │
│  Roam Freely. Forget.   │  ~130dp │  Roam Freely. Forget Nothing.             │  ~130dp ← eats 33%
│  4 memories             │         │  4 memories                               │
├─────────────────────────┤         ├───────────────────────────────────────────┤
│ tag chips               │         │ tag chips                                 │
│ entries …               │         │ ← ~200dp left for everything              │
└─────────────────────────┘         └───────────────────────────────────────────┘

LANDSCAPE (new — compact)
┌───────────────────────────────────────────────────────────┐
│  ☰  🐒 macaco · 4 memories                           [MI] │  ~52dp ← single row
├───────────────────────────────────────────────────────────┤
│ tag chips / entries fill the rest …                       │
└───────────────────────────────────────────────────────────┘
```

**Fix:** Inside `topBar = { … }`, read `isLandscape` and branch the entire header Block.

```kotlin
// BEFORE — topBar lambda in JournalListScreen (lines ~381–486)
// The entire topBar is one tall Box with a centred Column overlaid by a Row.
// (portrait-only — no landscape check exists)

topBar = {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .padding(bottom = 4.dp)
    ) {
        Row(                          // menu + avatar
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { /* … IconButton(menu) … Spacer(weight 1f) … avatar … */ }

        Column(                       // centred brand block
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(/* 48dp icon */)
            Column(modifier = Modifier.offset(y = (-10).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("macaco", fontSize = 13.sp, letterSpacing = 3.sp, …)
                Text("Roam Freely. Forget Nothing.", fontSize = 9.sp, …)
                if (entries.isNotEmpty()) { Text(memoriesText, …) }
            }
        }
    }
}

// AFTER — add an isLandscape branch at the top of the topBar lambda
topBar = {
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .padding(bottom = if (isLandscape) 0.dp else 4.dp)
    ) {
        if (isLandscape) {
            // ── Compact landscape header: single slim Row ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.journal_list_menu_cd),
                        tint = Color.White)
                }
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "macaco",
                    color = SplashGoldBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp
                )
                if (entries.isNotEmpty()) {
                    val count = visibleEntries.size
                    val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                    Text(
                        text = " · $memoriesText" +
                               if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = SplashGold.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.weight(1f))
                // Re-use the existing profile avatar block unchanged
                if (currentUser != null) {
                    if (profilePhotoUri != null) {
                        AsyncImage(
                            model = profilePhotoUri,
                            contentDescription = stringResource(R.string.common_profile),
                            modifier = Modifier
                                .padding(end = 12.dp).size(28.dp)
                                .clip(CircleShape).clickable { onProfile() },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp).size(28.dp)
                                .clip(CircleShape).background(Color.White)
                                .clickable { onProfile() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser!!.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SplashTealMid
                            )
                        }
                    }
                }
            }
        } else {
            // ── Existing portrait header — no changes ──────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { /* existing menu + avatar row — unchanged */ }

            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { /* existing centred brand column — unchanged */ }
        }
    }
},
```

**New import for `JournalListScreen.kt`:**
```kotlin
import androidx.compose.ui.platform.LocalConfiguration
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 2 — Adventures Map: compact header in landscape

**Problem:** The map header is a tall centred Column (logo 44 dp + "macaco" 20 sp + "Adventures"
label + mapped-locations count). In landscape it consumes ~120 dp of a ~390 dp screen, leaving
only ~210 dp of actual map, squeezed further by the nav bar.

```
PORTRAIT (fine)             LANDSCAPE (broken — ~120dp header)
┌──────────────────┐         ┌──────────────────────────────────────────┐
│  🐒              │         │  🐒  macaco · Adventures · 4/4 mapped    │ ← compact (new)
│  macaco          │  ~120dp └──────────────────────────────────────────┘
│  Adventures      │         ┌──────────────────────────────────────────┐
│  4 of 4 mapped   │         │                                          │
├──────────────────┤         │              MAP                         │
│       MAP        │         │                                          │
└──────────────────┘         └──────────────────────────────────────────┘
```

**Fix:** Add an `isLandscape` branch to the map's header `Box` (MapScreen.kt lines ~188–232).

```kotlin
// BEFORE — MapScreen.kt, map header Box (lines ~188–232)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painter = painterResource(R.drawable.ic_launcher_foreground),
              contentDescription = null, modifier = Modifier.size(44.dp).offset(y = 4.dp))
        Text("macaco", color = SplashGoldBright, fontSize = 20.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp)
        Text("Adventures", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        if (locations.isNotEmpty()) {
            Text("$mappedCount of ${locations.size} locations mapped",
                 color = SplashGold.copy(alpha = 0.70f), fontSize = 11.sp, fontFamily = MacacoFontFamily)
        }
    }
}

// AFTER
val isLandscape = LocalConfiguration.current.screenHeightDp < 480

Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(macacoBrandBackground())
        .statusBarsPadding()
) {
    if (isLandscape) {
        // Compact landscape header: single slim Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp
            )
            Text(
                text = " · Adventures",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (locations.isNotEmpty()) {
                Text(
                    text = " · $mappedCount/${locations.size} mapped",
                    color = SplashGold.copy(alpha = 0.70f),
                    fontSize = 11.sp,
                    fontFamily = MacacoFontFamily
                )
            }
        }
    } else {
        // Existing portrait header — unchanged
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { /* existing content unchanged */ }
    }
}
```

**New import for `MapScreen.kt`:**
```kotlin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Row   // if not already imported
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 3 — Entry Detail: two-panel landscape layout

**Problem:** The entry detail is a `LazyColumn` with the hero photo as the first item.
In landscape the hero is `260.dp` tall (or 52 % of screen height on sw600dp+), which on a
~390 dp landscape phone means the hero alone fills 67–100 % of the visible area.
Users arriving at an entry in landscape see only the photo and must scroll to find the title,
date, and description — the text is completely hidden below the fold.

```
PORTRAIT (good)                 LANDSCAPE now (broken)
┌─────────────────────┐         ┌─────────────────────────────────────────┐
│  ← title  ✈ 🗑       │  top    │  ← 2/4  ✈ 🗑                            │  top bar
├─────────────────────┤  bar    ├─────────────────────────────────────────┤
│                     │         │                                         │
│   HERO PHOTO        │  52%    │           HERO PHOTO                    │  ~260dp
│                     │  ht     │           (67% of landscape height)     │
├─────────────────────┤         │                                         │
│ [ 1 ][ 2 ][ 3 ][ + ]│  thumbs │ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │  fold ← almost here
├─────────────────────┤         │ [ 1 ][ 2 ][ 3 ][ + ]                   │
│ Tokyo, Japan        │         └─────────────────────────────────────────┘
│ 📍 Japan  📅 May 12 │  text   Title / content only visible after scroll
│ Incredible city …   │
│ #city #travel …     │
└─────────────────────┘

LANDSCAPE (new — two-panel)
┌─────────────────────────────────────────────────────────────────────┐
│  ← 2/4  ✈ 🗑                                                         │  top bar
├───────────────────────────────┬─────────────────────────────────────┤
│                               │  Tokyo, Japan                       │
│         HERO PHOTO            │  📍 Japan  📅 May 12, 2026          │
│         (full panel height)   │  Incredible city, amazing food …    │
│                               │  #city #travel #food                │
│         45% width             │  [ 1 ][ 2 ][ 3 ][ + ]  thumbs      │
│                               │  55% width (scrollable)             │
└───────────────────────────────┴─────────────────────────────────────┘
```

**Fix:** Inside the HorizontalPager page composable (the `Box` that wraps the `LazyColumn`),
check `isLandscape`. When true and the entry has photos, switch to a `Row` with a static photo
panel on the left and a `LazyColumn` (text + thumbnail strip) on the right.
When false (portrait) or the entry has no photos, use the existing `LazyColumn` unchanged.

The `configuration` variable is already declared near the top of the page composable
(line ~326: `val configuration = LocalConfiguration.current`). Re-use it.

```kotlin
// BEFORE — page composable, inner Box (line ~290 onward)
Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { scaleX = pageScale; scaleY = pageScale; alpha = pageAlpha }
) {
    if (entry.description.isBlank()) { MacacoWatermarkBackground(...) }
    photoActionIndex?.let { /* PhotoActionSheet */ }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {                           // ← photo block
            Box(modifier = Modifier.fillMaxWidth().clipToBounds()
                    .graphicsLayer { translationX = pageOffset * size.width * 0.4f }) {
                when {
                    photoCount == 0 -> /* mood emoji placeholder */
                    photoCount == 1 -> JournalPhoto(modifier = Modifier.fillMaxWidth().height(heroHeight))
                    else -> /* editorial collage at height(heroHeight) */
                }
                AnimatedVisibility(showCoverHint) { /* hint text */ }
            }
        }
        item {                           // ← text block
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                /* title, chips, description, tags */
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// AFTER
val isLandscape = configuration.screenHeightDp < 480

Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { scaleX = pageScale; scaleY = pageScale; alpha = pageAlpha }
) {
    if (entry.description.isBlank()) { MacacoWatermarkBackground(modifier = Modifier.matchParentSize()) }
    photoActionIndex?.let { idx -> PhotoActionSheet(/* unchanged */) }

    if (isLandscape && photoCount > 0) {
        // ── Two-panel landscape layout ─────────────────────────────────────
        Row(modifier = Modifier.fillMaxSize()) {

            // Left panel: photo collage fills the panel height
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .graphicsLayer { translationX = pageOffset * size.width * 0.4f }
            ) {
                when {
                    photoCount == 1 -> JournalPhoto(
                        data = entry.displayPhotoUri(0, cachedDrivePhotos),
                        onClick = { galleryStartIndex = 0 },
                        onLongClick = { photoActionIndex = 0 },
                        modifier = Modifier.fillMaxSize()         // ← fillMaxSize, not height(heroHeight)
                    )
                    else -> {
                        // Editorial collage — same structure but fills the panel, not heroHeight
                        val rightCount = if (photoCount <= 4) photoCount - 1 else 2
                        val overflowStart = 1 + rightCount
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                JournalPhoto(
                                    data = entry.displayPhotoUri(0, cachedDrivePhotos),
                                    onClick = { galleryStartIndex = 0 },
                                    onLongClick = { photoActionIndex = 0 },
                                    modifier = Modifier.weight(0.65f).fillMaxHeight()
                                )
                                Column(
                                    modifier = Modifier.weight(0.35f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (index in 1..rightCount) {
                                        JournalThumb(
                                            data = entry.displayPhotoUri(index, cachedDrivePhotos),
                                            onClick = { galleryStartIndex = index },
                                            onLongClick = { photoActionIndex = index },
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        )
                                    }
                                    if (photoCount <= 4) {
                                        AddPhotoTile(
                                            onClick = launchAddPhoto,
                                            modifier = Modifier.weight(1f).fillMaxWidth()
                                        )
                                    }
                                }
                            }
                            if (photoCount > 4) {
                                Spacer(Modifier.height(2.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(photoCount - overflowStart) { j ->
                                        val index = j + overflowStart
                                        JournalThumb(
                                            data = entry.displayPhotoUri(index, cachedDrivePhotos),
                                            onClick = { galleryStartIndex = index },
                                            onLongClick = { photoActionIndex = index },
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                    item { AddPhotoTile(onClick = launchAddPhoto, modifier = Modifier.size(64.dp)) }
                                }
                            }
                        }
                    }
                }
            }

            // Right panel: text content, scrollable
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
            ) {
                item {
                    AnimatedVisibility(
                        visible = showCoverHint,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(500))
                    ) {
                        Text(
                            text = stringResource(R.string.entry_detail_cover_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                }
                item {
                    // Text block — same content as portrait, padding reduced for landscape
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // title, FlowRow chips, trip row, description, tags
                        // ← copy the existing text-block Column content verbatim
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    } else {
        // ── Existing portrait LazyColumn — no changes ──────────────────────
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { /* existing photo block — unchanged */ }
            item { /* existing text block — unchanged */ }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
```

**No new imports needed** — `Row`, `fillMaxHeight`, and `LocalConfiguration` are already used
in `EntryDetailScreen.kt`. Verify `import androidx.compose.foundation.layout.Row` is present.

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/EntryDetailScreen.kt`

---

## Scope

- **In:** Portrait layouts on all three screens are **completely unchanged**. The `isLandscape`
  branch is purely additive.
- **In:** Entry detail landscape layout applies only when `photoCount > 0`. Entries with no
  photos (mood emoji placeholder) keep the portrait single-column layout in landscape — the
  placeholder is small and the text content is the main event.
- **Out:** New Entry / Edit Entry form in landscape — not tackled here; the keyboard-plus-form
  layout in landscape is a separate problem.
- **Out:** Two-column entry card grid in landscape — worthwhile but a separate brief.
- **No string changes. No ViewModel changes.**

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Compact single-row header in landscape (`screenHeightDp < 480`) | `JournalListScreen.kt` |
| 2 | Compact single-row header in landscape | `MapScreen.kt` |
| 3 | Two-panel landscape layout (photo left, scrollable text right) for entries with photos | `EntryDetailScreen.kt` |
