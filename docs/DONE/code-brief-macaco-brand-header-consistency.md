# Macaco — Brand Header: One Icon Size, One Wordmark Style, Everywhere

The monkey icon + "macaco" wordmark in the top brand block is a different size on every screen
(Journal, Adventures, Profile, Settings, Help & About), in both portrait and landscape — and the
same mismatch shows up again on tablet (confirmed via tablet screenshots of Adventures, Profile,
and Help & About, all showing oversized/undersized icon+title compared to Journal). This has
been patched screen-by-screen at least five times before (see `docs/DONE/code-brief-journal-header-icon-size.md`,
`code-brief-landscape-compact-header.md`, `code-brief-landscape-header-icon.md`,
`code-brief-profile-header-adventure-style.md`) and keeps drifting back out of sync because each
screen hand-copies its own `Image`/`Text` pair with its own dp/sp values. This brief fixes it for
good: extract a single shared composable, `MacacoBrandBlock`, sized to match **Journal's current
header exactly** (the user's chosen reference), and make every other screen call it instead of
hand-rolling its own icon+title block. Touches 6 files: 1 new, 5 edited
(`JournalListScreen.kt`, `MapScreen.kt`, `ProfileScreen.kt`, `SettingsScreen.kt`,
`HelpAboutScreen.kt`). The fix is a single shared composable keyed only on dp/sp values and an
`isLandscape` boolean — no per-device-size code exists or is added — so it applies uniformly to
phones and tablets alike (see **Tablet coverage** below).

**Reference values (from Journal, do not change these):**

| State | Icon | "macaco" text |
|---|---|---|
| Portrait (expanded) | 48dp | 13sp, letterSpacing 3sp, `FontWeight.Light` |
| Landscape (expanded) | 48dp | 16sp, letterSpacing 4sp, `FontWeight.Light` |
| Collapsed (icon only) | 48dp | — |

---

## Change 1 — New shared composable

**Problem:** No single source of truth for the brand block, so every screen drifts independently.

**Fix:** Add `MacacoBrandBlock`, a composable with three render modes (collapsed / landscape /
portrait) matching Journal's current layout exactly. It exposes two optional trailing slots so
each screen can still show its own page label / counts, stacked under "macaco" in portrait or
inline beside it in landscape — everything else (icon size, wordmark size, weight, spacing,
layout shape) is fixed.

**New file:** `app/src/main/java/com/houseofmmminq/macaco/ui/components/MacacoBrandBlock.kt`

```kotlin
package com.houseofmmminq.macaco.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.screens.SplashGoldBright

/** Single fixed icon size for the brand block in every state — matches Journal's header. */
private val MacacoBrandIconSize = 48.dp

/**
 * Shared "macaco" brand block (icon + wordmark), used by every top-level screen's header so the
 * icon size and wordmark style can never drift out of sync again. Sizes are fixed to Journal's
 * current header (the canonical reference); only the trailing page-label/count content is
 * per-screen, passed in via [portraitTrailing] / [landscapeTrailing].
 *
 * - [collapsed]: icon alone, centred — no wordmark. Used for scroll-collapsed or very short
 *   headers.
 * - [isLandscape] (ignored if [collapsed]): icon above a single Row of "macaco" + [landscapeTrailing].
 * - Otherwise (portrait, expanded): icon above a Column of "macaco" + [portraitTrailing], pulled
 *   up 10dp so it sits snug under the icon (matches Journal).
 */
@Composable
fun MacacoBrandBlock(
    isLandscape: Boolean,
    collapsed: Boolean = false,
    modifier: Modifier = Modifier,
    portraitTrailing: @Composable ColumnScope.() -> Unit = {},
    landscapeTrailing: @Composable RowScope.() -> Unit = {},
) {
    when {
        collapsed -> {
            Box(
                modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
            }
        }
        isLandscape -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 4.sp
                    )
                    landscapeTrailing()
                }
            }
        }
        else -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(MacacoBrandIconSize)
                )
                Column(
                    modifier = Modifier.offset(y = (-10).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp
                    )
                    portraitTrailing()
                }
            }
        }
    }
}
```

(Written `androidx.compose.material3.Text` fully-qualified inline to avoid an ambiguous-import
note — feel free to import `androidx.compose.material3.Text` normally at the top instead, same
as every other screen does.)

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/components/MacacoBrandBlock.kt` (new)

---

## Change 2 — JournalListScreen: use the shared block

**Problem:** N/A — Journal is the reference. This change is pure refactor (extract, no visual
change) so Journal's rendered output must stay pixel-identical.

**Fix:** Replace the three hand-rolled blocks (collapsed / landscape / portrait) with calls to
`MacacoBrandBlock`, passing the existing count `Text` as the trailing slot.

### Collapsed block (~lines 220–232)

```kotlin
// BEFORE
if (collapsed) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }
} else {
```

```kotlin
// AFTER
if (collapsed) {
    MacacoBrandBlock(isLandscape = isLandscape, collapsed = true)
} else {
```

### Landscape block (~lines 240–277) — only the icon+text Column, not the avatar Box that follows

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(48.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp
        )
        if (entries.isNotEmpty()) {
            val count = visibleEntries.size
            val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
            Text(
                text = " · " + memoriesText +
                    if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = SplashGold.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = true,
    modifier = Modifier.padding(vertical = 4.dp)
) {
    if (entries.isNotEmpty()) {
        val count = visibleEntries.size
        val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
        Text(
            text = " · " + memoriesText +
                if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = SplashGold.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

(This trailing lambda is `landscapeTrailing`, matched positionally — since `portraitTrailing`
has a default, Kotlin allows passing only the last lambda via trailing-lambda syntax as long as
you call the named-parameter form; if the compiler complains about which slot it binds to, name
it explicitly: `landscapeTrailing = { ... }`.)

### Portrait block (~lines 363–399)

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(48.dp)
    )
    Column(
        modifier = Modifier.offset(y = (-10).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp
        )
        if (entries.isNotEmpty()) {
            val count = visibleEntries.size
            val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
            Text(
                memoriesText + if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = SplashGold.copy(alpha = 0.8f)
            )
        }
    }
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier.align(Alignment.TopCenter)
) {
    if (entries.isNotEmpty()) {
        val count = visibleEntries.size
        val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
        Text(
            memoriesText + if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = SplashGold.copy(alpha = 0.8f)
        )
    }
}
```

Add `import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock` at the top.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 3 — MapScreen (Adventures): match Journal's sizes

**Problem:** Portrait icon is already 48dp (correct) but the wordmark is 20sp/5sp — much bigger
than Journal's 13sp/3sp. Landscape icon is already 48dp (correct) but the wordmark is 13sp/3sp —
that's Journal's *portrait* size, not its landscape size (16sp/4sp). Both orientations need to
adopt the shared block so this can't recur.

**Fix:** Same pattern — swap the hand-rolled icon+text for `MacacoBrandBlock`, moving the
existing title/count/globe-hint text into the trailing slot.

### Landscape block (~lines 418–471)

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // Line 1: icon alone, centred
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(48.dp)
    )
    // Line 2: wordmark + title + location count + globe hint, all in one row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp
        )
        Text(
            text = " · " + stringResource(R.string.map_adventures_title),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
        if (locations.isNotEmpty()) {
            val mappedCount = locations.count { it in geocodedLocations }
            Text(
                text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
                color = SplashGold.copy(alpha = 0.70f),
                fontSize = 12.sp,
                fontFamily = MacacoFontFamily
            )
        }
        if (globeSpanning) {
            Text(
                text = " · " + stringResource(R.string.map_globe_spanning_hint),
                style = MaterialTheme.typography.labelSmall,
                color = SplashGold.copy(alpha = 0.75f),
                letterSpacing = 0.5.sp
            )
        }
    }
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = true,
    modifier = Modifier.padding(vertical = 4.dp)
) {
    Text(
        text = " · " + stringResource(R.string.map_adventures_title),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f)
    )
    if (locations.isNotEmpty()) {
        val mappedCount = locations.count { it in geocodedLocations }
        Text(
            text = " · " + stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
            color = SplashGold.copy(alpha = 0.70f),
            fontSize = 12.sp,
            fontFamily = MacacoFontFamily
        )
    }
    if (globeSpanning) {
        Text(
            text = " · " + stringResource(R.string.map_globe_spanning_hint),
            style = MaterialTheme.typography.labelSmall,
            color = SplashGold.copy(alpha = 0.75f),
            letterSpacing = 0.5.sp
        )
    }
}
```

### Portrait block (~lines 472–510+, continues past line 504 shown — keep everything after the
wordmark `Text` as-is, just delete the `Image` + first `Text` and replace with `MacacoBrandBlock`)

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(top = 2.dp, bottom = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(48.dp)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 5.sp
    )
    Text(
        stringResource(R.string.map_adventures_title),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f)
    )
    if (locations.isNotEmpty()) {
        val mappedCount = locations.count { it in geocodedLocations }
        Text(
            stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
            color = SplashGold.copy(alpha = 0.70f)
        )
    }
    // ...rest of portrait block (globe-spanning hint etc.) continues unchanged below
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier
        .align(Alignment.Center)
        .padding(top = 2.dp, bottom = 10.dp)
) {
    Text(
        stringResource(R.string.map_adventures_title),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f)
    )
    if (locations.isNotEmpty()) {
        val mappedCount = locations.count { it in geocodedLocations }
        Text(
            stringResource(R.string.map_locations_mapped, mappedCount, locations.size),
            color = SplashGold.copy(alpha = 0.70f)
        )
    }
    // ...rest of portrait block (globe-spanning hint etc.) continues unchanged, still inside
    // this trailing lambda / ColumnScope
}
```

Read past line 504 in the actual file to find the full remainder of the portrait Column (globe
hint etc. — not shown in this brief) and carry all of it into the trailing lambda unchanged; only
the icon `Image` and the first "macaco" `Text` are deleted.

Add `import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock` at the top.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 4 — ProfileScreen: match Journal's sizes, add a landscape variant

**Problem:** Icon is 36dp (should be 48dp), wordmark is 18sp/5sp (should be 13sp/3sp portrait).
There is also no landscape-specific handling at all today — on a short landscape screen this
full-size block just stays full-size, unlike every other screen. Profile has no page-label
subtitle in the current design (screenshot shows just icon + "macaco", nothing else), so pass no
trailing content — this matches Journal exactly, as the user asked.

**Fix:** Add an `isLandscape` check (same pattern as the other screens) and swap the icon+text
Column for `MacacoBrandBlock` with no trailing slots.

### BEFORE (~lines 306–350)

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // Branded banner: splash teal radial with the back button and gold "macaco" wordmark.
    // The avatar below overlaps its bottom edge.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
    ) {
        // Profile is a bottom-nav tab (reached via the nav bar), so a back arrow here
        // is redundant — the brand block stands alone, centred.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .offset(y = 4.dp)
            )
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 5.sp
            )
        }
    }
```

### AFTER

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    // Branded banner: splash teal radial with the gold "macaco" wordmark.
    // The avatar below overlaps its bottom edge.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
    ) {
        // Profile is a bottom-nav tab (reached via the nav bar), so a back arrow here
        // is redundant — the brand block stands alone, centred. No page-label subtitle,
        // to match Journal's header exactly.
        MacacoBrandBlock(
            isLandscape = isLandscape,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 4.dp, bottom = if (isLandscape) 12.dp else 32.dp)
        )
    }
```

Note the `bottom` padding is reduced to 12dp in landscape — the 32dp original exists to leave
room for the avatar to overlap the banner's bottom edge (see the `.offset(y = (-32).dp)` a few
lines below in the same file); check that overlap still looks right in landscape once this
lands (the avatar itself may want a smaller overlap offset in landscape too — use judgement,
this wasn't in the original screenshots).

Add `import androidx.compose.ui.platform.LocalConfiguration` (if not already imported) and
`import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock` at the top.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Change 5 — SettingsScreen: match Journal's sizes

**Problem:** Landscape icon is 22dp (should be 48dp) with the wordmark inline in a Row (should be
icon-above-text, matching Journal/Adventures/Help&About's landscape layout). Portrait icon is
44dp (should be 48dp), wordmark 20sp/5sp (should be 13sp/3sp).

**Fix:** Swap both blocks for `MacacoBrandBlock`, keeping the back button exactly where it is and
moving the "Settings" page-label into the trailing slot.

### Landscape block (~lines 360–404) — keep the `IconButton` back arrow untouched, only replace
the inner `Column`

```kotlin
// BEFORE
Column(
    modifier = Modifier.align(Alignment.Center),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(22.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 3.sp
        )
        Text(
            text = " · " + stringResource(R.string.common_settings),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = true,
    modifier = Modifier.align(Alignment.Center)
) {
    Text(
        text = " · " + stringResource(R.string.common_settings),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f)
    )
}
```

### Portrait block (~lines 418–441) — keep the `IconButton` back arrow untouched, only replace
the inner `Column`

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(top = 2.dp, bottom = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(44.dp).offset(y = 4.dp)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 5.sp
    )
    Text(
        stringResource(R.string.common_settings),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f)
    )
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier
        .align(Alignment.Center)
        .padding(top = 2.dp, bottom = 10.dp)
) {
    Text(
        stringResource(R.string.common_settings),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f)
    )
}
```

Add `import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock` at the top.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/SettingsScreen.kt`

---

## Change 6 — HelpAboutScreen: match Journal's sizes

**Problem:** Icon is 64dp in all three states (collapsed / landscape / portrait) — bigger than
every other screen's 48dp. Portrait wordmark is 22sp/6sp (should be 13sp/3sp). Landscape wordmark
is already 16sp/4sp, which happens to already match Journal's landscape size — only its icon
needs fixing.

**Fix:** Same swap. The portrait block additionally has a slogan + version line beneath the page
label — keep those, just move them (unchanged) into the trailing slot alongside the "Help & About"
label.

### Collapsed block (~lines 156–170)

```kotlin
// BEFORE
Box(
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    contentAlignment = Alignment.Center
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(64.dp)
    )
}
```

```kotlin
// AFTER
MacacoBrandBlock(isLandscape = isLandscape, collapsed = true)
```

### Landscape block (~lines 171–214) — keep the `IconButton` back arrow untouched, only replace
the inner `Column`

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(64.dp)
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp
        )
        Text(
            text = " · " + stringResource(R.string.help_title),
            style = MaterialTheme.typography.labelMedium,
            color = SplashGold.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = true,
    modifier = Modifier.padding(vertical = 4.dp)
) {
    Text(
        text = " · " + stringResource(R.string.help_title),
        style = MaterialTheme.typography.labelMedium,
        color = SplashGold.copy(alpha = 0.7f),
        maxLines = 1
    )
}
```

### Portrait block (~lines 216–266)

```kotlin
// BEFORE
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(64.dp)
    )
    // No pull-up here: the icon stands alone on its own row with clear space above the wordmark.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "macaco",
            color = SplashGoldBright,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 6.sp
        )
        Text(
            text = "Roam Freely. Forget Nothing.",
            color = SplashGold.copy(alpha = 0.82f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(R.string.settings_version_value, versionLabel),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
```

```kotlin
// AFTER
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
) {
    Text(
        text = "Roam Freely. Forget Nothing.",
        color = SplashGold.copy(alpha = 0.82f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.size(4.dp))
    Text(
        stringResource(R.string.settings_version_value, versionLabel),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.8f)
    )
}
```

Note the "Help & About" page-label line itself (which the old code didn't actually show in
portrait — only the slogan + version) stays out of the trailing slot exactly as before; only the
icon size and wordmark size change. If Code finds the portrait block was meant to also show a
"Help & About" label (compare against the landscape block, which does show one via
`R.string.help_title`), that's a separate, smaller inconsistency worth flagging back rather than
silently fixing — not part of this brief's scope.

Add `import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock` at the top.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

---

## Tablet coverage

**Problem:** The user confirmed via tablet screenshots that Adventures, Profile, and Help & About
show the same oversized/mismatched icon+title on tablet as on phone (e.g. Adventures' oversized
"macaco" wordmark, Profile's cramped 36dp icon with almost no gap before the wordmark). User ask:
align these with Journal's icon/title on tablet too, not just on phone.

**Why this needs no extra code:** every screen's `isLandscape` check is
`LocalConfiguration.current.screenHeightDp < 480` — a height-in-dp threshold, not a device-type
check. `MapScreen.kt`'s own existing comment confirms the intent: *"Tablets stay tall (~750dp+)
and keep the full header"* — i.e. a tablet's `screenHeightDp` is always well above 480 even when
held in landscape, so every screen already falls into the **portrait/expanded branch** of its
header on tablet (matching what the tablet screenshots show: the full stacked icon-above-title
block, not the compact landscape row). There is no separate tablet layout path anywhere in these
5 files, and this brief does not add one.

**Consequence:** once Changes 2–6 land, tablets get Journal's reference sizing "for free" — the
expanded branch of `MacacoBrandBlock` (48dp icon, 13sp/3sp wordmark, offset -10dp) is exactly what
renders on Adventures/Profile/Settings/Help & About on a tablet, because they all resolve
`isLandscape = false` there today and will keep doing so. No tablet-specific `if` branches, no
`WindowSizeClass`, no `sw600dp` resource qualifiers — same code path as phone portrait.

**Verify after implementing:** on a tablet (or a large/resizable emulator), Adventures, Profile,
Settings, and Help & About should all show the same 48dp icon and the same 13sp/letterSpacing-3sp
"macaco" as Journal's header — since none of these four ever reach the `isLandscape` compact
branch on a tablet-sized screen, this is what confirms the fix actually landed there too.

---

## Out of scope

- No change to the cold-start `SplashScreen.kt` itself — the user's screenshots are all in-app
  headers (Journal/Adventures/Profile/Settings/Help&About topBars), not the launch splash. Leave
  `SplashScreen.kt` untouched; it already has its own distinct animated sizing.
- No change to `AppLockScreen.kt`, `LoginScreen.kt`, `PurchaseScreen.kt`,
  `SubscriptionInfoScreen.kt`, `OnboardingScreen.kt`, or `EntryDetailScreen.kt`'s icon usage —
  not mentioned in the screenshots and each has a different purpose (lock screen, auth, paywall).
  If the user wants those aligned too, that's a follow-up brief.
- Collapsed-state behavior (scroll-to-collapse) is unchanged — Journal and Help & About already
  collapse; Adventures, Profile, and Settings do not, and this brief doesn't add it. Only the
  *expanded* icon/wordmark sizing is being unified.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | New shared `MacacoBrandBlock` composable (48dp icon; 13sp/3sp portrait, 16sp/4sp landscape wordmark) | `ui/components/MacacoBrandBlock.kt` (new) |
| 2 | Refactor to use shared block (no visual change — this is the reference) | `JournalListScreen.kt` |
| 3 | Fix icon/wordmark size, use shared block | `MapScreen.kt` |
| 4 | Fix icon/wordmark size, add landscape variant, use shared block | `ProfileScreen.kt` |
| 5 | Fix icon/wordmark size, use shared block | `SettingsScreen.kt` |
| 6 | Fix icon/wordmark size, use shared block | `HelpAboutScreen.kt` |
