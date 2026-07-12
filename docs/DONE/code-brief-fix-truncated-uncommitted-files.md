# Macaco — Fix Truncated/Corrupted Uncommitted Edits (Build-Breaking)

The working tree currently does not build. Four files were mid-edit (implementing
`docs/code-brief-help-about-and-year-in-travel-fixes.md` and
`docs/code-brief-retire-trip-share-link.md`) when the write was interrupted, and each one got
cut off at an arbitrary byte with no closing tag/brace: `strings.xml`, `JournalListScreen.kt`,
`HelpAboutScreen.kt`, and `YearInTravelScreen.kt`. This brief diagnoses each corruption precisely
(confirmed via `git diff` against HEAD) and gives the exact restoration needed. The intentional
feature edits already present in these files (collapsible FAQ sections, trip-share-link removal,
Year in Travel scroll fix) are correct and should be **kept** — only the truncated tail of each
file needs fixing.

Two files, `ic_launcher.xml` and `ic_launcher_round.xml`, also show as fully modified in `git
diff` but the content is byte-identical to HEAD except for line endings (CRLF vs LF) — this is
noise, not corruption, and needs no action beyond confirming it doesn't block the build.

---

## Change 1 — Restore `strings.xml` (invalid XML, unrelated strings dropped)

**Problem:** The file ends mid-token at `<string name="com` with no closing quote, no closing
`</string>`, and no `</resources>` — this alone is fatal (invalid XML, won't compile as an
Android resource). Additionally, the edit accidentally deleted several string entries that are
**still needed** by other, unrelated features (collapsible-sections work, Print Book FAQ, Year in
Travel FAQ, search/weather FAQs, photo-suggestion plurals) — these were collateral damage from
the interrupted write, not an intentional removal.

Two entries in the deleted range — `help_section_trip_sharing` and
`help_faq_trip_sharing_q`/`_a` — should **not** be restored, since `HelpAboutScreen.kt` (Change 3
below) and `JournalListScreen.kt` (Change 2 below) already correctly remove the Trip Sharing UI
that used them, per `docs/code-brief-retire-trip-share-link.md`.

**Fix:** Replace the final line of the file (the truncated `<string name="com` with no trailing
newline) with the following, restoring everything except the trip-sharing-specific keys:

```xml
<!-- CURRENT (broken) tail — replace this entire line: -->
    <string name="com

<!-- RESTORE — insert immediately after help_faq_video_length_a, replacing the line above: -->
    <string name="common_expand">Expand</string>
    <string name="common_collapse">Collapse</string>
    <string name="help_section_print_export">Print &amp; Export</string>
    <string name="help_section_year_recap">Year in Travel</string>
    <string name="help_faq_search_q">How do I search my entries?</string>
    <string name="help_faq_search_a">Tap the search icon on the journal screen and start typing — Macaco matches as you type across title, location, description, and tags.</string>
    <string name="help_faq_weather_q">Where does the weather stamp come from?</string>
    <string name="help_faq_weather_a">Macaco automatically looks up the historical weather for an entry\'s location and date and shows it as a small chip next to mood and location. It\'s fetched once in the background — there\'s nothing to turn on, and it only appears once weather data is available for that date.</string>
    <string name="help_faq_print_q">Can I print my journal as a book?</string>
    <string name="help_faq_print_a">Yes — open Settings and tap \"Print Book\" (premium). Pick entries by trip, location, or a custom selection, and Macaco lays them out into a print-ready A4 PDF with a custom cover, opening page, and a branded closing page. Save the PDF or share it straight to a print shop or home printer — Macaco doesn\'t handle print fulfillment itself.</string>
    <string name="help_faq_year_recap_q">What is Year in Travel?</string>
    <string name="help_faq_year_recap_a">Open your profile and tap \"Year in Travel\" for a recap of that year\'s journalling — your busiest month, most common mood, most-used tag, and more. Tap \"Share my year\" to turn it into an image you can post or send.</string>
    <plurals name="photo_suggestion_photo_count">
        <item quantity="one">%d photo</item>
        <item quantity="other">%d photos</item>
    </plurals>
    <plurals name="photo_suggestion_visit_count">
        <item quantity="one">%d visit</item>
        <item quantity="other">%d visits</item>
    </plurals>
</resources>
```

Note what's deliberately **not** in the restored block versus the original pre-corruption
version: `help_section_trip_sharing` and `help_faq_trip_sharing_q`/`_a` are omitted (correctly
retired). If the full `retire-trip-share-link` brief hasn't been run yet, the 13 `trip_share_*`
string keys used by the now-removed `ShareTripDialog` (`trip_share_action`, `trip_share_title`,
`trip_share_disclosure`, `trip_share_expiry_*`, `trip_share_create`, `trip_share_copy`,
`trip_share_copied`, `trip_share_revoke`, `trip_share_revoked`, `trip_share_error_generic`) still
exist earlier in the file, untouched by this corruption — they're now unused resources but won't
break the build. Clean those up as part of `docs/code-brief-retire-trip-share-link.md`, not this
brief.

Verify afterward with `xmllint --noout app/src/main/res/values/strings.xml` (or equivalent) to
confirm valid XML before moving on. **This restoration is for `values/strings.xml` (default/
English) only** — check whether the other 10 locale `strings.xml` files have the same
corruption (`git status`/`git diff` only flagged the default one, so they're likely untouched,
but confirm).

**File(s):** `app/src/main/res/values/strings.xml`

---

## Change 2 — Fix `JournalListScreen.kt` (garbage trailing line after EOF)

**Problem:** The trip-share-link UI removal itself (dropping the `Share` icon import, the
`shareDialogTrip`/`tripShareState` state, the `ShareTripDialog`/`ExpiryOption` composables, and
the `onShare` param on `TripHeader`) is correct and complete — matches
`docs/code-brief-retire-trip-share-link.md`. The only problem is the file's last line: after the
closing `}` of `MonthHeader`, there's a single enormous line of blank padding characters with no
trailing newline, which breaks the Kotlin parser.

**Fix:** Delete everything after the final `}` that closes `MonthHeader` (the corrupted trailing
line) and make sure the file ends with that `}` followed by a normal newline — no other content
after it.

```kotlin
// The file must end exactly here (this is the existing, correct end of MonthHeader):
private fun MonthHeader(month: String, collapsed: Boolean, onToggleCollapse: () -> Unit): Unit {
    // ...existing body, unchanged...
}
// ← delete the long garbage whitespace line that currently follows this brace.
```

**File(s):** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 3 — Fix `HelpAboutScreen.kt` (truncated mid-string in `HelpActionRow`)

**Problem:** The header/version-label rework and the removal of the "Trip Sharing" `FaqSection`
(along with the now-unused `Share` icon import) are correct and complete. But the file cuts off
mid-argument inside `HelpActionRow`, at `subti` with no closing quote — the last several lines of
the composable (and the file) never got written.

**Fix:** Restore the tail of `HelpActionRow`, which is unchanged from HEAD — nothing in this
section needed to change:

```kotlin
// CURRENT (broken) — file ends here:
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subti

// AFTER — restore the full, original composable body and closing braces:
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

**File(s):** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

---

## Change 4 — Fix `YearInTravelScreen.kt` (truncated mid-argument in share button)

**Problem:** The scroll-fix (`verticalScroll(rememberScrollState())` + `navigationBarsPadding()`
added to the content `Modifier`) is correct and complete. The file cuts off mid-call at
`context.getString(R.string.year_recap_share_chooser` with no closing paren — the rest of the
share button and the closing braces for the composable are missing.

**Fix:** Restore the tail, unchanged from HEAD (nothing here needed to change beyond the earlier
scroll-modifier edit):

```kotlin
// CURRENT (broken) — file ends here:
                    context.startActivity(
                        Intent.createChooser(shareIntent, context.getString(R.string.year_recap_share_chooser

// AFTER — restore the closing calls and braces:
                    context.startActivity(
                        Intent.createChooser(shareIntent, context.getString(R.string.year_recap_share_chooser))
                    )
                },
                enabled = recap.entryCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.year_recap_share))
            }
        }
        }
    }
}
```

**File(s):** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/YearInTravelScreen.kt`

---

## Not a problem — no action needed

`ic_launcher.xml` and `ic_launcher_round.xml` show as fully changed in `git diff`, but the
content is byte-identical to HEAD — the diff is entirely a line-ending change (CRLF ↔ LF).
Confirm this with `git diff -- <file>` showing every line changed but no textual difference, and
leave as-is (or normalize line endings to match the repo convention if that bothers your editor,
but it isn't build-breaking).

---

## Verification

1. `xmllint --noout app/src/main/res/values/strings.xml` (or open it in Android Studio and
   confirm no red squiggles) — must parse as valid XML.
2. `./gradlew compileDebugKotlin` — must succeed with no syntax errors in the three `.kt` files.
3. `./gradlew assembleDebug` — full build should succeed.
4. Manually confirm in `HelpAboutScreen.kt` that the FAQ list still renders Print & Export,
   Year in Travel, search, and weather entries (i.e. the restored strings are actually wired up,
   not just present in `strings.xml` — they already were referenced in `FAQ_SECTIONS`, this just
   confirms nothing else was silently dropped).
5. Confirm no remaining reference to `ShareTripDialog`, `shareDialogTrip`, `tripShareState`, or
   `Icons.Filled.Share` in `JournalListScreen.kt` — if `JournalViewModel.kt` and
   `TripShareManager.kt` still exist with the old trip-share plumbing, that's expected and out of
   scope here (covered by `docs/code-brief-retire-trip-share-link.md` if not already done).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Restore invalid/truncated XML, re-add collaterally-deleted strings, keep trip-sharing keys removed | `strings.xml` |
| 2 | Strip garbage trailing line after `MonthHeader`, restore valid Kotlin EOF | `JournalListScreen.kt` |
| 3 | Restore truncated tail of `HelpActionRow` | `HelpAboutScreen.kt` |
| 4 | Restore truncated tail of the Year in Travel share button | `YearInTravelScreen.kt` |
| — | No action — line-ending-only diff | `ic_launcher.xml`, `ic_launcher_round.xml` |
