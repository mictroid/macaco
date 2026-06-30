# Macaco — Help & About v2: Adventure Reel, free trial, and map edge FAQ entries

Adds three new FAQ entries to `HelpAboutScreen.kt` and matching string keys to all 11
`strings.xml` files. No layout changes — uses the existing `FaqSection` / `FaqCard` pattern.

Read `docs/DONE/code-brief-help-about-update.md` for prior FAQ additions context.

---

## Change 1 — Fix stale reorder answer + add Set as Cover entry

The previous help brief added `help_faq_reorder_photos_a` with instructions for edit-mode
drag reordering — but the shipped implementation (`code-brief-photo-management.md`) uses a
**long-press bottom sheet** from the entry detail view instead. The answer is wrong and needs
correcting. Set as Cover has no FAQ entry at all.

### 1a — Update `help_faq_reorder_photos_a` in all 11 `strings.xml` files

```xml
<!-- BEFORE -->
<string name="help_faq_reorder_photos_a">Open the entry in edit mode. Long-press any photo
and drag it to a new position to reorder. Tap the × on a photo to remove it from the entry
(the photo stays in your gallery).</string>

<!-- AFTER — reflects the shipped bottom-sheet approach -->
<string name="help_faq_reorder_photos_a">Open the entry and long-press any photo in the
thumbnail strip. A menu appears with options to move the photo left or right, remove it from
the entry, or set it as the cover image. The photo stays in your gallery when removed from
an entry.</string>
```

### 1b — Add Set as Cover string key

```xml
<!-- NEW — Photos section, add after help_faq_reorder_photos_* -->
<string name="help_faq_cover_q">How do I change the cover photo of an entry?</string>
<string name="help_faq_cover_a">Open the entry and long-press any photo in the thumbnail
strip, then tap \"Set as cover\". The chosen photo becomes the hero image at the top of the
entry and the thumbnail shown in the journal list.</string>
```

### 1c — Wire Set as Cover into the Photos FAQ section (`HelpAboutScreen.kt`, line ~73)

```kotlin
// BEFORE
FaqSection(
    R.string.help_section_photos,
    listOf(
        R.string.help_faq_q_photos to R.string.help_faq_a_photos,
        R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
        R.string.help_faq_q_backup to R.string.help_faq_a_backup,
    )
),

// AFTER
FaqSection(
    R.string.help_section_photos,
    listOf(
        R.string.help_faq_q_photos to R.string.help_faq_a_photos,
        R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
        R.string.help_faq_cover_q to R.string.help_faq_cover_a,           // ← add
        R.string.help_faq_q_backup to R.string.help_faq_a_backup,
    )
),
```

---

## Change 2 — New string keys (English)

Add to `app/src/main/res/values/strings.xml` alongside the existing `help_faq_*` strings:

```xml
<!-- Adventure Reel — goes in Premium section -->
<string name="help_faq_reel_q">What is the Adventure Reel?</string>
<string name="help_faq_reel_a">The Adventure Reel turns all the photos from a trip into a short cinematic video — perfect for sharing or keeping as a memento. Tap the 🎬 button on any trip header in the journal list to create one. Macaco handles the rest: Ken Burns animation, smooth transitions, and your location and date on each photo. Adventure Reel is a premium feature.</string>

<!-- Free trial — goes in Premium section -->
<string name="help_faq_free_trial_q">Is there a free trial?</string>
<string name="help_faq_free_trial_a">Yes — the Annual plan includes a 7-day free trial. You won\'t be charged until the trial ends, and you can cancel at any time during the trial without being billed. Monthly and Lifetime plans do not include a trial period.</string>

<!-- Adventures map globe-spanning — goes in Getting Started section, after the existing adventures map entry -->
<string name="help_faq_map_pins_q">Why are some pins missing from the Adventures map?</string>
<string name="help_faq_map_pins_a">When your adventures span more than roughly half the globe — for example, entries in both Japan and Argentina — the map can\'t show all pins at once at a readable zoom level. Tap the ◀ or ▶ arrows at the map edges to jump to the pins that are off-screen. The header shows how many locations have been mapped so you know if any are missing.</string>
```

Translate all six strings into the remaining 10 languages:
`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`,
`values-pl`, `values-pt`, `values-sv`, `values-zh-rCN`.

---

## Change 3 — Wire into `FAQ_SECTIONS` in `HelpAboutScreen.kt`

### 2a — Getting Started section: add globe-spanning map entry (line ~67)

```kotlin
// BEFORE
FaqSection(
    R.string.help_section_getting_started,
    listOf(
        R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
        R.string.help_faq_reset_password_q to R.string.help_faq_reset_password_a,
        R.string.help_faq_use_tags_q to R.string.help_faq_use_tags_a,
        R.string.help_faq_swipe_entries_q to R.string.help_faq_swipe_entries_a,
        R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
    )
),

// AFTER — add map pins entry after the existing adventures map entry
FaqSection(
    R.string.help_section_getting_started,
    listOf(
        R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
        R.string.help_faq_reset_password_q to R.string.help_faq_reset_password_a,
        R.string.help_faq_use_tags_q to R.string.help_faq_use_tags_a,
        R.string.help_faq_swipe_entries_q to R.string.help_faq_swipe_entries_a,
        R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
        R.string.help_faq_map_pins_q to R.string.help_faq_map_pins_a,   // ← add
    )
),
```

### 2b — Premium section: add Reel and free trial entries (line ~93)

```kotlin
// BEFORE
FaqSection(
    R.string.help_section_premium,
    listOf(
        R.string.help_faq_premium_benefits_q to R.string.help_faq_premium_benefits_a,
        R.string.help_faq_premium_broken_q to R.string.help_faq_premium_broken_a,
        R.string.help_faq_cancel_plan_q to R.string.help_faq_a_billing,
    )
),

// AFTER — free trial first (billing question), then Reel, then existing entries
FaqSection(
    R.string.help_section_premium,
    listOf(
        R.string.help_faq_free_trial_q to R.string.help_faq_free_trial_a,          // ← add
        R.string.help_faq_reel_q to R.string.help_faq_reel_a,                      // ← add
        R.string.help_faq_premium_benefits_q to R.string.help_faq_premium_benefits_a,
        R.string.help_faq_premium_broken_q to R.string.help_faq_premium_broken_a,
        R.string.help_faq_cancel_plan_q to R.string.help_faq_a_billing,
    )
),
```

---

## Scope

- **In:** Three new FAQ entries (free trial, Adventure Reel, globe-spanning map pins) in
  English + 10 translated languages. Wired into existing `FAQ_SECTIONS`.
- **Out:** Layout changes to `HelpAboutScreen.kt` — the existing design handles new entries
  automatically via the `FAQ_SECTIONS` list.
- **Out:** Updating existing FAQ answer copy — those entries remain unchanged.

---

## Verification

1. Open Help & About → Getting Started → last entry should be "Why are some pins missing
   from the Adventures map?" with the ◀▶ explanation.
2. Open Premium section → first two entries should be "Is there a free trial?" and "What is
   the Adventure Reel?", followed by the existing three entries.
3. Check one non-English locale (e.g. German) — new entries should appear translated, not in
   English fallback.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | 3 new FAQ Q/A string pairs | `values/strings.xml` × 11 languages |
| 2 | Wire into Getting Started + Premium sections | `HelpAboutScreen.kt` |
