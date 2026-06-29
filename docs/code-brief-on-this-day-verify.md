# Macaco — JournalListScreen: On This Day — awareness + verification

No new implementation needed. This brief documents the existing On This Day feature so it
is not accidentally broken in future changes, and asks for an on-device verification pass.

---

## What already exists

`OnThisDayBanner` and `OnThisDayEntryChip` are already implemented in
`JournalListScreen.kt` (lines ~1020–1165). The matching logic lives in
`TravelEntry.onThisDayEntries()` in `data/model/TravelEntry.kt` (line 27).

**How it works:**
- On every app open, `onThisDayEntries()` filters the loaded entry list for entries whose
  calendar month + day-of-month match today's date, from any prior year.
- If at least one match exists, `OnThisDayBanner` renders above the tag filter row and
  below the search bar.
- The banner is a `primaryContainer` card with a horizontal scroll row of entry chips —
  each chip shows a photo thumbnail (or mood emoji fallback), "X years ago" in the primary
  colour, the entry title, and location.
- Tapping a chip navigates to that entry via `onEntryClick(entry.id)`.
- An ✕ button dismisses the banner for the current session (`remember { mutableStateOf(false) }`
  — reappears on next app open, which is correct behaviour).

**Where it sits in the layout (JournalListScreen.kt ~line 594):**
```
LazyColumn / scrollable body
  ├── OnThisDayBanner   ← appears here when entries match, above tag filters
  ├── TagFilterRow
  └── entry list / EmptyState
```

---

## Verification task

The feature is only visible when at least one entry has a date matching today's
calendar day from a prior year — it may not have been seen in recent testing.

Please verify the following during the next debug build:

1. **Temporarily override today's date** in `onThisDayEntries()` to force a match — change
   `Calendar.getInstance()` to a hardcoded date that matches an existing entry's day/month
   (e.g. if there's an entry dated 2024-06-29, set today to any June 29). Confirm the banner
   renders, chips are tappable, and the ✕ dismiss works. Then revert the override.

2. **Dark mode** — confirm the chip card border (the `0.5.dp outlineVariant` border applied
   in dark mode at line ~1104) renders cleanly.

3. **Single entry vs multiple entries** — confirm horizontal scroll works when there are 2+
   matching entries.

If anything looks off, fix it and note what changed. No new strings, no ViewModel changes,
no navigation changes expected.

---

## Scope

- **In:** read-only verification + any minor visual fixes found during testing.
- **Out:** changing the dismiss behaviour (per-session is correct).
- **Out:** changing the matching logic (day + month match is correct).
- **No new strings. No new files.**

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Verify OnThisDayBanner renders, chips navigate correctly, dismiss works | `JournalListScreen.kt` |
| 2 | Verify dark mode chip border and multi-entry horizontal scroll | `JournalListScreen.kt` |
