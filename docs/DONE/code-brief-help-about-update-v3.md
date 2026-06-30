# Macaco — Help & About v3: stale answers + four missing FAQ entries

One file primary: `HelpAboutScreen.kt`. String changes across all 11 `strings.xml` files.

Read `docs/DONE/code-brief-help-about-update-v2.md` for the previous FAQ additions.

---

## Fix 1 — Correct stale `premium_broken` answer

`help_faq_premium_broken_a` tells users to "open Settings, scroll to Subscription" — but
Subscription was moved to the Profile screen (`code-brief-subscription-to-profile`). Users
following the old instructions won't find it.

Update in all 11 `strings.xml` files:

```xml
<!-- BEFORE -->
<string name="help_faq_premium_broken_a">Try restoring your purchase: open Settings, scroll
to Subscription, and tap Restore purchase. Make sure you\'re signed in with the same account
used when you bought. If it still doesn\'t work, contact us at houseofmmminq@gmail.com.</string>

<!-- AFTER -->
<string name="help_faq_premium_broken_a">Try restoring your purchase: tap your profile icon,
then tap Subscription, and tap Restore purchase. Make sure you\'re signed in with the same
account used when you bought. If it still doesn\'t work, contact us at
houseofmmminq@gmail.com.</string>
```

---

## Fix 2 — Add Adventure Reel to premium benefits list

`help_faq_premium_benefits_a` lists the premium features but omits Adventure Reel. Update
in all 11 `strings.xml` files:

```xml
<!-- BEFORE -->
<string name="help_faq_premium_benefits_a">Premium unlocks the full Macaco experience:
unlimited journal entries, the Adventures map, swipe between entries, custom themes, and
priority support. Available as Monthly, Annual, or Lifetime.</string>

<!-- AFTER -->
<string name="help_faq_premium_benefits_a">Premium unlocks the full Macaco experience:
unlimited journal entries, the Adventures map, Adventure Reel video slideshows, swipe between
entries, custom themes, and Google Drive photo backup. Available as Monthly, Annual (with
7-day free trial), or Lifetime.</string>
```

---

## Fix 3 — New FAQ: account deletion

No FAQ explains what Delete Account does or how to find it, despite it being a prominent
(and irreversible) action in Profile. Add under a new **Account** section.

### 3a — New strings (all 11 `strings.xml` files)

```xml
<string name="help_section_account">Account</string>
<string name="help_faq_delete_account_q">How do I delete my account?</string>
<string name="help_faq_delete_account_a">Open your profile and tap Delete Account at the
bottom. This permanently deletes all your journal entries from the cloud and removes your
account — it cannot be undone. Your photos stay in your device gallery. If you just want to
sign out, use Sign Out instead.</string>
```

### 3b — Add new Account section to `FAQ_SECTIONS` in `HelpAboutScreen.kt`

Add after the existing Privacy section (line ~90):

```kotlin
// BEFORE — Privacy section is last before Premium
FaqSection(
    R.string.help_section_privacy,
    listOf(
        R.string.help_faq_q_lock to R.string.help_faq_a_lock,
    )
),
FaqSection(
    R.string.help_section_premium,
    ...
)

// AFTER — new Account section between Privacy and Premium
FaqSection(
    R.string.help_section_privacy,
    listOf(
        R.string.help_faq_q_lock to R.string.help_faq_a_lock,
    )
),
FaqSection(
    R.string.help_section_account,                              // ← new section
    listOf(
        R.string.help_faq_delete_account_q to R.string.help_faq_delete_account_a,
    )
),
FaqSection(
    R.string.help_section_premium,
    ...
)
```

---

## Fix 4 — New FAQ: On This Day

The On This Day banner appears at the top of the journal list on anniversary dates. No FAQ
explains what it is. Add under Getting Started.

### 4a — New strings (all 11 `strings.xml` files)

```xml
<string name="help_faq_on_this_day_q">What is "On This Day"?</string>
<string name="help_faq_on_this_day_a">When you have entries from the same date in a previous
year, Macaco shows an "On This Day" banner at the top of your journal. Tap it to revisit
those memories. The banner only appears on the matching calendar date each year.</string>
```

### 4b — Add to Getting Started section in `FAQ_SECTIONS` (line ~61)

```kotlin
// AFTER the existing adventures_map entry
R.string.help_faq_adventures_map_q to R.string.help_faq_adventures_map_a,
R.string.help_faq_map_pins_q to R.string.help_faq_map_pins_a,
R.string.help_faq_on_this_day_q to R.string.help_faq_on_this_day_a,    // ← add
```

---

## Fix 5 — New FAQ: How to connect Google Drive

The Photos FAQ mentions Drive ("Connect Google Drive in Settings") without explaining how.
Users who don't have Drive connected won't know the path. Add a dedicated entry.

### 5a — New strings (all 11 `strings.xml` files)

```xml
<string name="help_faq_drive_connect_q">How do I connect Google Drive for photo backup?</string>
<string name="help_faq_drive_connect_a">Go to Settings → Drive Sync and tap Connect Google
Drive. Sign in with a Google account — this can be the same account you use to log in, or a
different one. Once connected, Macaco automatically backs up entry photos to a "Macaco" folder
in your Drive and restores them on any device you sign in to.</string>
```

### 5b — Add to Photos section in `FAQ_SECTIONS` (line ~73)

```kotlin
// AFTER the existing photos answer, before reorder
R.string.help_faq_q_photos to R.string.help_faq_a_photos,
R.string.help_faq_drive_connect_q to R.string.help_faq_drive_connect_a,    // ← add
R.string.help_faq_reorder_photos_q to R.string.help_faq_reorder_photos_a,
```

---

## Fix 6 — New FAQ: What are trips

Trips group entries in the journal list under a shared header. The Profile screen counts
them. No FAQ explains the concept or how to use it.

### 6a — New strings (all 11 `strings.xml` files)

```xml
<string name="help_faq_trips_q">What are trips?</string>
<string name="help_faq_trips_a">A trip is a label that groups related entries together in
your journal. When you create or edit an entry, type a trip name in the Trip field — all
entries with the same name are grouped under one header in the journal list. Trips are
optional; entries without one appear ungrouped.</string>
```

### 6b — Add to Getting Started section in `FAQ_SECTIONS`

```kotlin
// Add after the create_entry entry — trips are a fundamental concept
R.string.help_faq_create_entry_q to R.string.help_faq_create_entry_a,
R.string.help_faq_trips_q to R.string.help_faq_trips_a,                 // ← add
R.string.help_faq_reset_password_q to R.string.help_faq_reset_password_a,
```

---

## Scope

- **In:** Two stale answer corrections; four new FAQ entries (account deletion, On This Day,
  Drive connection, trips); new Account section in `FAQ_SECTIONS`.
- **Out:** Layout changes to `HelpAboutScreen.kt` — the list-driven pattern handles new
  entries automatically.
- **Out:** FAQ entries for mood, voice input, sharing — those are self-explanatory from the UI.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Fix `premium_broken` answer — Subscription is in Profile, not Settings | `strings.xml` ×11 |
| 2 | Add Adventure Reel + Drive to `premium_benefits` feature list | `strings.xml` ×11 |
| 3 | New Account section + Delete Account FAQ | `strings.xml` ×11, `HelpAboutScreen.kt` |
| 4 | New On This Day FAQ under Getting Started | `strings.xml` ×11, `HelpAboutScreen.kt` |
| 5 | New Drive connection FAQ under Photos | `strings.xml` ×11, `HelpAboutScreen.kt` |
| 6 | New Trips FAQ under Getting Started | `strings.xml` ×11, `HelpAboutScreen.kt` |
