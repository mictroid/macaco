# Macaco — Help & About: fix the stale widget list in the FAQ

Post-vc74 audit found `help_faq_widget_a` (in `strings.xml` × all 11 locales) describes a widget
lineup that no longer matches what actually ships. Single string-content fix, no layout/code
changes.

---

## Widget FAQ answer names a widget that doesn't exist and omits one that does

**Problem:** `help_faq_widget_a` (EN, `app/src/main/res/values/strings.xml` line 91) reads:

> "Yes — long-press your home screen, choose Widgets, and add one of Macaco's widgets: "On This
> Day" (a memory from this date in a previous year, or your latest entry), "Quick Add" (a shortcut
> straight to a new entry), "Travel Stats" (your entry and place counts), or "**Adventures**" (how
> many places you've mapped). Note: On This Day, Travel Stats, and **Adventures** stay visible even
> when App Lock is on..."

There is no "Adventures" widget anywhere in the codebase — grepping
`app/src/main/java/.../ui/widget/` finds exactly four providers: `OnThisDayWidgetProvider`,
`RecentEntriesWidgetProvider` (+ `RecentEntriesWidgetService`), `TravelStatsWidgetProvider`,
`QuickAddWidgetProvider`. ("Adventures" is the in-app map tab — `MapScreen` — not a widget.) The
real fourth widget, **Recent Entries** (shipped in the vc74 widgets batch, confirmed in
`docs/worklog-2026-07-19.md` and present as `settings_widget_recent` / `widget_label_recent` in
every locale's `strings.xml`), isn't mentioned at all. This is stale copy left over from an
earlier planning stage, predating the actual vc74 widget lineup — confirmed present in all 11
locale files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`,
`values-nl`, `values-pl`, `values-pt`, `values-sv`, `values-zh-rCN`), so it's a copy/paste
propagation, not an English-only slip.

**Fix:** Replace "Quick Add" / "Adventures" mix-up with the real four: On This Day, Recent
Entries, Travel Stats, Quick Add. Recent Entries shows journal data like On This Day and Travel
Stats, so it belongs in the App-Lock-visibility caveat sentence alongside them; Quick Add stays
the one exception (shows no journal data).

`app/src/main/res/values/strings.xml` (EN master — line 91):

```xml
<!-- BEFORE -->
<string name="help_faq_widget_a">Yes — long-press your home screen, choose Widgets, and add one of Macaco\'s widgets: \"On This Day\" (a memory from this date in a previous year, or your latest entry), \"Quick Add\" (a shortcut straight to a new entry), \"Travel Stats\" (your entry and place counts), or \"Adventures\" (how many places you\'ve mapped). Note: On This Day, Travel Stats, and Adventures stay visible even when App Lock is on, so remove them if you\'d rather keep your journal off the home screen — Quick Add shows no journal data, so it isn\'t affected.</string>

<!-- AFTER -->
<string name="help_faq_widget_a">Yes — long-press your home screen, choose Widgets, and add one of Macaco\'s widgets: \"On This Day\" (a memory from this date in a previous year, or your latest entry), \"Recent Entries\" (a scrollable list of your latest journal entries), \"Travel Stats\" (your entry and place counts), or \"Quick Add\" (a shortcut straight to a new entry). Note: On This Day, Recent Entries, and Travel Stats stay visible even when App Lock is on, so remove them if you\'d rather keep your journal off the home screen — Quick Add shows no journal data, so it isn\'t affected.</string>
```

**Localization:** this is a content correction to an *existing* key in all 11 locales, not a new
key — translate the corrected English meaning into each locale's own language (matching that
locale's existing translated widget names, e.g. `widget_label_recent`'s translated value in that
locale), the same way the vc73 `help-about-widget-drive-faq` brief and the vc74
`drive-dedupe-and-doc-updates` brief's FAQ-string updates were done (bulk-localize-strings skill
recipe: BOM-on-script, no-BOM-on-XML PowerShell). Do not just copy the English sentence into the
other 10 files.

| Key | File | Change |
|-----|------|--------|
| `help_faq_widget_a` | `values/strings.xml` + 10 locale variants | Swap "Quick Add"/"Adventures" ordering + drop "Adventures" reference for the real "Recent Entries" widget, in both the widget list and the App-Lock caveat sentence |

**Files:** `app/src/main/res/values/strings.xml` and the 10 `values-*/strings.xml` locale
variants (`de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`, `sv`, `zh-rCN`).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Fix stale widget FAQ answer (remove fictional "Adventures" widget, add real "Recent Entries") | `strings.xml` × 11 locales |
