# Macaco — Help & About: collapsible sections, version in header, themed icons, 5 new FAQ entries

`HelpAboutScreen.kt` has grown to six FAQ sections rendered fully expanded — a long, uniform
scroll with no visual variety and no way to jump past a section you already know. This brief
makes each section collapsible (mirroring the trip/month collapse pattern already briefed for
`JournalListScreen` in `docs/code-brief-reel-icon-placement-and-collapsible-groups.md` — read
that section first for the established chevron/clickable-row convention), pins the app version
to the header so it's visible regardless of scroll position, gives every section header a
themed icon, and documents five shipped-but-undocumented features: Print Book export, entry
search, Year in Travel recap, trip sharing links, and the weather stamp. Two files touched, one
new set of strings: `ui/screens/HelpAboutScreen.kt`, `res/values*/strings.xml` (×11 languages).

---

## Change 1 — Collapsible FAQ sections

**Problem:** `FAQ_SECTIONS.forEach` (lines 255-260) renders every section's `SectionHeader` and
all its `FaqCard`s unconditionally. There's no collapse state and `SectionHeader` (lines 294-303)
takes only a `text: String` — no icon, no toggle affordance.

**Fix:** Track collapsed section keys in session-scoped Compose state (same convention as the
journal-list brief: `remember`, not persisted — every fresh screen open starts fully expanded).
Key by `titleRes` (unique per section, already an `Int`). Give `SectionHeader` an optional icon
and an optional `onToggleCollapse` — optional because the existing "Get in touch" header
(Change 4) should keep an icon but stay always-expanded, not collapsible.

**File:** `ui/screens/HelpAboutScreen.kt` (imports, top of file)

```kotlin
// ADD
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

(`getValue` and `remember` are already imported at lines 43/45.)

**File:** `ui/screens/HelpAboutScreen.kt` (inside `HelpAboutScreen`, next to the existing
`scrollState`/`collapsed` header-scroll state, ~line 138-142 — same area, unrelated state, add
alongside it)

```kotlin
// ADD
    // Collapsible FAQ sections: which section keys (FaqSection.titleRes) are currently
    // collapsed. Session-scoped only (remember, not rememberSaveable/DataStore) — matches the
    // journal list's trip/month collapse convention: a browsing convenience, not a persisted
    // setting. Every fresh screen open starts fully expanded.
    var collapsedSections by remember { mutableStateOf(setOf<Int>()) }
    fun toggleSection(key: Int) {
        collapsedSections =
            if (key in collapsedSections) collapsedSections - key else collapsedSections + key
    }
```

**File:** `ui/screens/HelpAboutScreen.kt` (the FAQ render loop, ~line 254-260)

```kotlin
// BEFORE
            // ── FAQ, grouped into named sections ──
            FAQ_SECTIONS.forEach { section ->
                SectionHeader(stringResource(section.titleRes))
                section.items.forEach { (q, a) ->
                    FaqCard(question = stringResource(q), answer = stringResource(a))
                }
            }
```

```kotlin
// AFTER
            // ── FAQ, grouped into named, collapsible sections ──
            FAQ_SECTIONS.forEach { section ->
                val isCollapsed = section.titleRes in collapsedSections
                SectionHeader(
                    text = stringResource(section.titleRes),
                    icon = section.icon,
                    collapsed = isCollapsed,
                    onToggleCollapse = { toggleSection(section.titleRes) }
                )
                if (!isCollapsed) {
                    section.items.forEach { (q, a) ->
                        FaqCard(question = stringResource(q), answer = stringResource(a))
                    }
                }
            }
```

**File:** `ui/screens/HelpAboutScreen.kt` (`SectionHeader`, lines 294-303)

```kotlin
// BEFORE
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}
```

```kotlin
// AFTER
@Composable
private fun SectionHeader(
    text: String,
    icon: ImageVector? = null,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggleCollapse != null) Modifier.clickable(onClick = onToggleCollapse) else Modifier)
            .padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (onToggleCollapse != null) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed)
                    stringResource(R.string.common_expand) else stringResource(R.string.common_collapse),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
```

Note: if the journal-list collapsible-groups brief has already shipped, `common_expand` /
`common_collapse` already exist in `strings.xml` — reuse them, don't re-add.

**Out of scope:** collapsing the "Get in touch" block (Change 4) — it's four short, essential
action rows (contact, privacy, terms, listing), not a long FAQ list, and stays always visible.
A single "collapse all" toggle was also not requested — each section toggles independently, same
as the journal-list brief.

---

## Change 2 — Themed icon per FAQ section

**Problem:** `FaqSection` (line 65) has no icon field, so every section header looks identical
except for its label — the page reads as a wall of uniform grey text.

**Fix:** Add an `icon: ImageVector` to `FaqSection` and assign a Macaco-flavoured icon per topic
— one that hints at the section's theme (a compass for getting started, a passport-style
document for account, a monkey-relevant `Explore`/globe for adventures) while staying inside
Material's icon set so no new drawables are needed.

**File:** `ui/screens/HelpAboutScreen.kt` (imports, top of file)

```kotlin
// ADD
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Shield
```

**File:** `ui/screens/HelpAboutScreen.kt` (`FaqSection` data class + `FAQ_SECTIONS`, lines 65-124)

```kotlin
// BEFORE
private data class FaqSection(val titleRes: Int, val items: List<Pair<Int, Int>>)

private val FAQ_SECTIONS = listOf(
    FaqSection(
        R.string.help_section_getting_started,
        listOf( /* … existing items … */ )
    ),
    FaqSection(
        R.string.help_section_media,
        listOf( /* … existing items … */ )
    ),
    FaqSection(
        R.string.help_section_sync,
        listOf( /* … existing items … */ )
    ),
    FaqSection(
        R.string.help_section_privacy,
        listOf( /* … existing items … */ )
    ),
    FaqSection(
        R.string.help_section_account,
        listOf( /* … existing items … */ )
    ),
    FaqSection(
        R.string.help_section_premium,
        listOf( /* … existing items … */ )
    ),
)
```

```kotlin
// AFTER
private data class FaqSection(val titleRes: Int, val icon: ImageVector, val items: List<Pair<Int, Int>>)

private val FAQ_SECTIONS = listOf(
    FaqSection(
        R.string.help_section_getting_started,
        Icons.Filled.Explore,
        listOf(
            R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
            R.string.help_faq_trips_q to R.string.help_faq_trips_a,
            R.string.help_faq_reset_password_q to R.string.help_faq_reset_password_a,
            R.string.help_faq_use_tags_q to R.string.help_faq_use_tags_a,
            R.string.help_faq_swipe_entries_q to R.string.help_faq_swipe_entries_a,
            R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
            R.string.help_faq_map_pins_q to R.string.help_faq_map_pins_a,
            R.string.help_faq_on_this_day_q to R.string.help_faq_on_this_day_a,
            // NEW (Change 5): entry search
            R.string.help_faq_search_q to R.string.help_faq_search_a,
            // NEW (Change 5): weather stamp
            R.string.help_faq_weather_q to R.string.help_faq_weather_a,
        )
    ),
    FaqSection(
        R.string.help_section_media,
        Icons.Filled.Photo,
        listOf(
            R.string.help_faq_q_photos to R.string.help_faq_a_photos,
            R.string.help_faq_video_add_q to R.string.help_faq_video_add_a,
            R.string.help_faq_video_length_q to R.string.help_faq_video_length_a,
            R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,
            R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
            R.string.help_faq_cover_q to R.string.help_faq_cover_a,
            R.string.help_faq_q_backup to R.string.help_faq_a_backup,
        )
    ),
    FaqSection(
        R.string.help_section_sync,
        Icons.Filled.Sync,
        listOf(
            R.string.help_faq_q_sync to R.string.help_faq_a_sync,
            R.string.help_faq_transfer_device_q to R.string.help_faq_transfer_device_a,
        )
    ),
    FaqSection(
        R.string.help_section_privacy,
        Icons.Outlined.Shield,
        listOf(
            R.string.help_faq_q_lock to R.string.help_faq_a_lock,
        )
    ),
    FaqSection(
        R.string.help_section_account,
        Icons.Filled.AccountCircle,
        listOf(
            R.string.help_faq_delete_account_q to R.string.help_faq_delete_account_a,
        )
    ),
    FaqSection(
        R.string.help_section_premium,
        Icons.Filled.WorkspacePremium,
        listOf(
            R.string.help_faq_free_trial_q to R.string.help_faq_free_trial_a,
            R.string.help_faq_reel_q to R.string.help_faq_reel_a,
            R.string.help_faq_premium_benefits_q to R.string.help_faq_premium_benefits_a,
            R.string.help_faq_premium_broken_q to R.string.help_faq_premium_broken_a,
            R.string.help_faq_cancel_plan_q to R.string.help_faq_a_billing,
        )
    ),
    // NEW (Change 5): Print Book export
    FaqSection(
        R.string.help_section_print_export,
        Icons.Filled.Print,
        listOf(
            R.string.help_faq_print_q to R.string.help_faq_print_a,
        )
    ),
    // NEW (Change 5): Year in Travel recap
    FaqSection(
        R.string.help_section_year_recap,
        Icons.Filled.CalendarMonth,
        listOf(
            R.string.help_faq_year_recap_q to R.string.help_faq_year_recap_a,
        )
    ),
    // NEW (Change 5): shared trip links
    FaqSection(
        R.string.help_section_trip_sharing,
        Icons.Filled.Share,
        listOf(
            R.string.help_faq_trip_sharing_q to R.string.help_faq_trip_sharing_a,
        )
    ),
)
```

**File:** `ui/screens/HelpAboutScreen.kt` (the "Get in touch" block, ~line 262-263 — give the
static header an icon too, no collapse)

```kotlin
// BEFORE
            // ── Get in touch ──
            SectionHeader(stringResource(R.string.help_get_in_touch))
```

```kotlin
// AFTER
            // ── Get in touch ──
            SectionHeader(stringResource(R.string.help_get_in_touch), icon = Icons.Filled.MailOutline)
```

**Design note — why three new sections, not five:** the request covered five undocumented
features (Print Book, Search, Year in Travel, Trip Sharing, Weather Stamp). Search and Weather
Stamp are entry-browsing basics in the same spirit as the existing "Getting Started" content
(tags, swipe, on-this-day), so they're added as two extra Q&As inside that section rather than
as their own single-question sections — keeps the page from getting three near-empty sections.
Print Book, Year in Travel, and Trip Sharing are distinct enough destinations (their own screens,
reached from Settings/Profile/trip header respectively) to earn dedicated sections.

---

## Change 3 — App version pinned to the header

**Problem:** the version label (`settings_version_value`) only renders in the portrait
at-rest header (lines 216-220, inside the `else` branch of the `when`). Scroll down, or rotate to
landscape, and it disappears — there's no way to check the version without scrolling back to top.

**Fix:** Move the version to a small label pinned to the header's top-end corner, as a sibling of
the `when` block rather than inside any one branch, so it's visible in all three header states
(collapsed, landscape, portrait) regardless of scroll position. Remove the now-redundant version
line from the portrait branch to avoid showing it twice.

**File:** `ui/screens/HelpAboutScreen.kt` (header `Box`, ~line 148-224)

```kotlin
// BEFORE (structure)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .animateContentSize()
            ) {
                when {
                    collapsed -> { /* … */ }
                    isLandscape -> { /* … */ }
                    else -> { /* … includes the versionLabel Text, lines 216-220 … */ }
                }
            }
```

```kotlin
// AFTER (structure)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .animateContentSize()
            ) {
                when {
                    collapsed -> { /* … unchanged … */ }
                    isLandscape -> { /* … unchanged … */ }
                    else -> { /* … versionLabel Text removed, see below … */ }
                }
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
            }
```

**File:** `ui/screens/HelpAboutScreen.kt` (portrait `else` branch, ~line 204-221 — drop the
now-duplicated version `Text`)

```kotlin
// BEFORE
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
                        }
```

**Out of scope:** `MacacoBrandBlock` itself (`ui/components/MacacoBrandBlock.kt`) is shared by
every top-level screen's header (Journal, Adventures, Entry Detail, etc.) and its `collapsed`
branch has no trailing-content slot by design. Adding the version there would affect every
screen's collapsed header, not just Help & About — out of scope for this brief. Pinning the
label to `HelpAboutScreen`'s own header `Box` (as above) keeps the change local.

---

## Change 4 — Five new FAQ entries for shipped-but-undocumented features

**Problem:** Print Book PDF export, entry search, Year in Travel recap, shared trip links, and
the weather stamp have all shipped (per `docs/DONE/code-brief-print-book-export.md`,
`code-brief-entry-search.md`, `code-brief-year-in-travel-recap.md` + `-v2`,
`code-brief-shared-trip-links.md`, `code-brief-weather-stamp.md`) but none have a Help & About
entry, so users have no in-app way to discover or understand them.

**Fix:** add the five Q&As referenced in Change 2's `FAQ_SECTIONS`, plus the three new section
headers, as new `strings.xml` entries.

**Localization:** the following new keys need adding to `res/values/strings.xml` and translating
across all 11 supported languages (`values-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`,
`-sv`, `-zh-rCN`).

| Key | EN value |
|-----|----------|
| `help_section_print_export` | Print & Export |
| `help_section_year_recap` | Year in Travel |
| `help_section_trip_sharing` | Trip Sharing |
| `help_faq_search_q` | How do I search my entries? |
| `help_faq_search_a` | Tap the search icon on the journal screen and start typing — Macaco matches as you type across title, location, description, and tags. |
| `help_faq_weather_q` | Where does the weather stamp come from? |
| `help_faq_weather_a` | Macaco automatically looks up the historical weather for an entry's location and date and shows it as a small chip next to mood and location. It's fetched once in the background — there's nothing to turn on, and it only appears once weather data is available for that date. |
| `help_faq_print_q` | Can I print my journal as a book? |
| `help_faq_print_a` | Yes — open Settings and tap "Print Book" (premium). Pick entries by trip, location, or a custom selection, and Macaco lays them out into a print-ready A4 PDF with a custom cover, opening page, and a branded closing page. Save the PDF or share it straight to a print shop or home printer — Macaco doesn't handle print fulfillment itself. |
| `help_faq_year_recap_q` | What is Year in Travel? |
| `help_faq_year_recap_a` | Open your profile and tap "Year in Travel" for a recap of that year's journalling — your busiest month, most common mood, most-used tag, and more. Tap "Share my year" to turn it into an image you can post or send. |
| `help_faq_trip_sharing_q` | Can I share a trip with someone who doesn't have Macaco? |
| `help_faq_trip_sharing_a` | Yes — tap Share on any trip header to create a view-only web link. Anyone with the link can see those entries and photos without installing the app or signing in. Set an expiry (7 days, 30 days, or never) and revoke access anytime from the same menu. |

`common_expand` / `common_collapse` are also needed (Change 1) if the journal-list collapsible-
groups brief hasn't already added them — check `strings.xml` first; don't duplicate.

**Verification:** open Help & About, confirm all nine section headers show an icon and a
chevron (except "Get in touch", which has an icon but no chevron and never collapses), tap each
chevron and confirm only that section's cards hide/show, scroll down and rotate to landscape and
confirm the version label in the top-right corner stays visible throughout, and confirm the five
new Q&As render with correct copy in at least one non-English locale (spot-check translations
aren't left as English fallback).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `collapsedSections` state + toggle; `SectionHeader` gains icon/collapse/chevron; FAQ loop skips collapsed sections | `ui/screens/HelpAboutScreen.kt` |
| 2 | `FaqSection` gains `icon: ImageVector`; themed icon assigned per section (existing 6 + new 3) | `ui/screens/HelpAboutScreen.kt` |
| 3 | Version label moved out of the portrait-only branch to a header-wide pinned `Text` (top-end, all states) | `ui/screens/HelpAboutScreen.kt` |
| 4 | 3 new sections (Print & Export, Year in Travel, Trip Sharing) + 2 new Q&As folded into Getting Started (Search, Weather) | `ui/screens/HelpAboutScreen.kt` |
| 5 | New string keys: 3 section titles + 10 Q&A strings (×11 languages) | `res/values*/strings.xml` |
