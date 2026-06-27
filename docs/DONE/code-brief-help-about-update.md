# Macaco — Help & About: Add three FAQ entries

Adds three new FAQ items to `HelpAboutScreen.kt` and the matching string keys to all 11
`strings.xml` files. No layout changes — uses the existing `FaqSection` / `FaqCard` pattern.

---

## Change 1 — Add new string keys (English)

Add these six new string resources to `app/src/main/res/values/strings.xml`, alongside the
existing `help_faq_*` strings (around line 73):

```xml
<!-- NEW — Getting Started -->
<string name="help_faq_reset_password_q">How do I reset my password?</string>
<string name="help_faq_reset_password_a">On the login screen, tap \"Forgot password?\" and enter your email. You\'ll receive a reset link in a few seconds. This only applies to email/password accounts — if you signed in with Google, use your Google account to manage your password.</string>

<!-- NEW — Photos -->
<string name="help_faq_reorder_photos_q">How do I reorder or remove photos?</string>
<string name="help_faq_reorder_photos_a">Open the entry in edit mode. Long-press any photo and drag it to a new position to reorder. Tap the × on a photo to remove it from the entry (the photo stays in your gallery).</string>

<!-- NEW — Getting Started -->
<string name="help_faq_use_tags_q">How do I use tags?</string>
<string name="help_faq_use_tags_a">Type a tag in the entry editor and press the + button to add it. Tags let you group entries by trip, theme, or any label you like. Tap a tag chip on any entry in the journal list to filter by that tag.</string>
```

File: `app/src/main/res/values/strings.xml`

Translate all six strings into the remaining 10 languages:
`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`,
`values-pl`, `values-pt`, `values-sv`, `values-zh-rCN`.

---

## Change 2 — Wire new strings into `FAQ_SECTIONS`

**Problem:** The three new FAQ entries have string keys but nothing references them yet.

**Fix:** Add `help_faq_reset_password_*` and `help_faq_use_tags_*` to the Getting Started
section, and `help_faq_reorder_photos_*` to the Photos section.

```kotlin
// BEFORE — Getting Started section (HelpAboutScreen.kt, lines ~60–67)
FaqSection(
    R.string.help_section_getting_started,
    listOf(
        R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
        R.string.help_faq_swipe_entries_q to R.string.help_faq_swipe_entries_a,
        R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
    )
),
FaqSection(
    R.string.help_section_photos,
    listOf(
        R.string.help_faq_q_photos to R.string.help_faq_a_photos,
        R.string.help_faq_q_backup to R.string.help_faq_a_backup,
    )
),

// AFTER
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
FaqSection(
    R.string.help_section_photos,
    listOf(
        R.string.help_faq_q_photos to R.string.help_faq_a_photos,
        R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
        R.string.help_faq_q_backup to R.string.help_faq_a_backup,
    )
),
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/HelpAboutScreen.kt`

---

## Scope

- **In:** Three new FAQ cards. String resources in all 11 languages. One Kotlin change in `FAQ_SECTIONS`.
- **Out:** No layout changes, no new composables, no ViewModel changes, no navigation changes.
- **No new imports needed** — `FAQ_SECTIONS` already references `R.string.*` constants.

---

## New string keys (English values for translation reference)

| Key | EN value |
|-----|----------|
| `help_faq_reset_password_q` | How do I reset my password? |
| `help_faq_reset_password_a` | On the login screen, tap "Forgot password?" and enter your email. You'll receive a reset link in a few seconds. This only applies to email/password accounts — if you signed in with Google, use your Google account to manage your password. |
| `help_faq_reorder_photos_q` | How do I reorder or remove photos? |
| `help_faq_reorder_photos_a` | Open the entry in edit mode. Long-press any photo and drag it to a new position to reorder. Tap the × on a photo to remove it from the entry (the photo stays in your gallery). |
| `help_faq_use_tags_q` | How do I use tags? |
| `help_faq_use_tags_a` | Type a tag in the entry editor and press the + button to add it. Tags let you group entries by trip, theme, or any label you like. Tap a tag chip on any entry in the journal list to filter by that tag. |

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Add 6 new `help_faq_*` string keys | `values/strings.xml` + 10 language variants |
| 2 | Add 3 FAQ entries to `FAQ_SECTIONS` (Getting Started × 2, Photos × 1) | `HelpAboutScreen.kt` |
