# Macaco — Help & About header/collapse fixes + Year in Travel inset/scroll bug

Three files touched: `HelpAboutScreen.kt` (collapse default, version placement, portrait title),
`YearInTravelScreen.kt` (missing nav-bar inset + missing scroll), `strings.xml` (no new keys —
reuses `help_title`, already present in all 11 languages).

**Not in scope, flagged separately:** the "permission denied" error when sharing a trip link is
not a code bug. `TripShareManager.kt`'s own header comment (and `docs/DONE/code-brief-shared-trip-links.md`)
already documents that `createShareLink()` requires Firestore + Storage security rules for
`/shared_trips` to be published in the Firebase console — this repo has no rules file to edit, and
Code cannot apply console-side rules. This needs Michael to publish the rules in the Firebase
console before the feature will work; no Kotlin change fixes it.

**Also not in scope:** Profile header collapse-on-scroll is already implemented (`ProfileScreen.kt`,
`collapsed` derived from `profileScrollState`) — it's just rarely visible on a typical portrait
phone because profile content doesn't overflow the screen. No change needed.

---

## Change 1 — Help & About: FAQ sections collapsed by default

**Problem:** `collapsedSections` starts as an empty set, so all 9 FAQ sections render fully
expanded on first open — a wall of ~35 Q&A cards before the user has scrolled at all.

**Fix:** Seed `collapsedSections` with every section's `titleRes` so the screen opens fully
collapsed; tapping a header expands just that section, same toggle logic as today.

```kotlin
// HelpAboutScreen.kt — BEFORE (~line 196)
var collapsedSections by remember { mutableStateOf(setOf<Int>()) }

// AFTER
var collapsedSections by remember {
    mutableStateOf(FAQ_SECTIONS.map { it.titleRes }.toSet())
}
```

**File:** `HelpAboutScreen.kt`.

---

## Change 2 — Help & About: version label moves from top-right corner to under "macaco"

**Problem:** The version `Text` (lines ~278-287) is pinned to `Alignment.TopEnd` on the outer
header `Box`, independent of the `MacacoBrandBlock` — it reads as a stray corner label rather than
part of the brand block, and is inconsistent with the rest of the app (Settings shows its label
centered under the wordmark, not in a corner).

**Fix:** Drop the `TopEnd`-aligned `Text`. Instead append the version as a second line inside
`portraitTrailing` / `landscapeTrailing`, right after the page-title line, so it's centered under
"macaco" in every header state. Hide it when `collapsed` (same as the page title/tagline already
disappear when collapsed) since the collapsed header has no text at all.

```
BEFORE                              AFTER
┌─────────────────────┐  version    ┌─────────────────────┐
│                      │◄── pinned   │        [icon]        │
│       [icon]         │   top-right │        macaco         │
│       macaco          │            │    Help & About        │
│                      │            │      v1.4.2 (68)        │
└─────────────────────┘             └─────────────────────┘
```

```kotlin
// HelpAboutScreen.kt — landscape branch (~line 220-246), BEFORE:
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

// AFTER — version appended on its own line below the "macaco · Help & About" row.
// landscapeTrailing is a RowScope, so wrap the row content + version line in a Column
// via a second MacacoBrandBlock content slot isn't available — instead pass a Column-wrapped
// custom composable directly under the block. Simplest: keep the Row as-is, and add the version
// as a *portrait-style* second line by changing the landscape branch to stack, matching the
// portrait treatment below (Column: title row, then version).
Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
    if (versionLabel.isNotEmpty()) {
        Text(
            text = stringResource(R.string.settings_version_value, versionLabel),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.65f)
        )
    }
}
```

```kotlin
// HelpAboutScreen.kt — portrait branch (~line 248-274), BEFORE:
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
}

// AFTER — tagline replaced with the page title (Change 3), version appended below it.
MacacoBrandBlock(
    isLandscape = false,
    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
) {
    Text(
        text = stringResource(R.string.help_title),
        style = MaterialTheme.typography.labelMedium,
        color = SplashGold.copy(alpha = 0.85f)
    )
    if (versionLabel.isNotEmpty()) {
        Text(
            text = stringResource(R.string.settings_version_value, versionLabel),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
```

```kotlin
// HelpAboutScreen.kt — DELETE the old pinned corner Text entirely (~line 276-287):
// Pinned outside the `when` so it stays visible in every header state —
// collapsed, landscape, and portrait-at-rest alike.
if (versionLabel.isNotEmpty()) {
    Text(
        text = stringResource(R.string.settings_version_value, versionLabel),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.65f),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 12.dp)
    )
}
```

Net effect: version is invisible only in the fully-collapsed (scrolled) header state, same as the
page title — acceptable since the header is icon-only there for every screen already.

**File:** `HelpAboutScreen.kt`.

---

## Change 3 — Help & About: portrait shows "Help & About" title (folded into Change 2's snippet above)

**Problem:** Landscape shows "macaco · Help & About"; portrait shows a marketing tagline instead
of the page name, unlike every other secondary screen (e.g. Settings shows "Settings" under
"macaco" in portrait). Approved fix: portrait shows the page title too, matching that convention.

**Fix:** Already included in the portrait-branch AFTER snippet in Change 2 — the tagline
`"Roam Freely. Forget Nothing."` is replaced with `stringResource(R.string.help_title)`. No new
string key needed; `help_title` already exists in all 11 `strings.xml` files (used by the
landscape branch today).

**File:** `HelpAboutScreen.kt` (same edit as Change 2).

---

## Change 4 — Year in Travel: Share button hidden under system nav bar + landscape unscrollable

**Problem:** `YearInTravelScreen.kt` opts out of Scaffold's automatic insets
(`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) and manually re-applies `statusBarsPadding()`
on the header, but never re-applies `navigationBarsPadding()` at the bottom, and the content
`Column` has no `verticalScroll`. Every other screen with this same opt-out pattern (Settings,
Profile, Help & About) adds both. Result: on devices with a gesture/3-button nav bar (reported on
a Galaxy A53), the "Share my year" button at the bottom of the fixed-height `Column` renders
partly or fully under the system nav bar; in landscape, where vertical space is tightest, content
overflows the screen with no way to scroll to the button at all.

**Fix:** Add `verticalScroll(rememberScrollState())` and `navigationBarsPadding()` to the content
`Column`, matching `SettingsScreen.kt`'s established pattern exactly.

```kotlin
// YearInTravelScreen.kt — imports, ADD:
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

```kotlin
// YearInTravelScreen.kt — BEFORE (~line 105-110):
Column(
    Modifier
        .padding(padding)
        .fillMaxSize()
        .padding(24.dp)
) {

// AFTER
Column(
    Modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(24.dp)
) {
```

**File:** `YearInTravelScreen.kt`.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | FAQ sections collapsed by default | `HelpAboutScreen.kt` |
| 2 | Version moved from top-right corner to centered under "macaco", hidden when collapsed | `HelpAboutScreen.kt` |
| 3 | Portrait header shows "Help & About" title (was tagline-only) | `HelpAboutScreen.kt` |
| 4 | Added `verticalScroll` + `navigationBarsPadding` — fixes Share button under system nav bar (A53) and unscrollable landscape | `YearInTravelScreen.kt` |

**Not fixed here (needs Firebase console access, not code):** trip-share-link "permission denied"
— publish the `/shared_trips` Firestore + Storage security rules per
`docs/DONE/code-brief-shared-trip-links.md`.

**Not fixed here (already works):** Profile header already collapses on scroll; just rarely
visible on portrait phones because content doesn't overflow.
