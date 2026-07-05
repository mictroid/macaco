---
name: bulk-localize-strings
model: sonnet
description: Add (or edit) string resource keys across all of Macaco's locale strings.xml files at once. Use when a brief or feature needs a new user-facing string, or when inserting/translating keys into res/values*/strings.xml. Handles the 11 locales and the PowerShell UTF-8/BOM trap that mangles CJK and accented translations.
---

# Bulk-localize strings across all locales

Macaco ships **11 locales**. Every user-facing string key must exist in all of them, or the
non-English builds fall back to English (or crash on a missing format arg). The locale files are:

```
app/src/main/res/values/strings.xml          (en — base)
app/src/main/res/values-fr/strings.xml        values-es   values-de   values-it
app/src/main/res/values-pt/strings.xml        values-nl   values-sv   values-pl
app/src/main/res/values-ja/strings.xml        values-zh-rCN
```

(All keys — including the `help_faq_*` FAQ strings — are translated in all 11 locales; verified
2026-06-27 (all 30 `help_faq_*` keys present in en/de/ja). An older note here and in the
`strings-localization` memory claimed FAQ strings were English-only — that's wrong; confirm against
live source if unsure.)

## The trap: PowerShell encoding mangles non-ASCII

The reliable way to insert the same key into all 11 files is a PowerShell script with per-locale
translations. **Two encoding pitfalls will silently corrupt CJK/accented text:**

1. **`powershell.exe -File script.ps1` reads the script as ANSI unless it has a UTF-8 BOM.** Without
   the BOM, every `…`, `ä`, `ä`, `正` in the script literals is mangled *before* it's ever written.
   → The script file itself must be **UTF-8 *with* BOM**.
2. **Output files must be written UTF-8 *without* BOM** (Android `strings.xml` must not have a BOM).
   → Write with `New-Object System.Text.UTF8Encoding($false)` (the `$false` = no BOM).

So: BOM on the *script*, no BOM on the *XML*. Mixing these up is what caused a failed run recently.

## Procedure

1. Copy `insert-strings.template.ps1` (next to this file) to the scratchpad and fill in:
   - `$anchor` — the `name="..."` of an existing key to insert **after** (pick a key in the same
     section so the new keys land logically, e.g. `settings_backup_failed`).
   - `$tr` — an ordered map of locale dir → array of translated values, one entry per new key, in
     a consistent order across locales. The `values` (English) row is the source of truth.
   - The `$block` builder — one `<string name="...">` line per new key, indented 4 spaces.
2. Save the script as **UTF-8 with BOM**. From Git Bash that's:
   ```bash
   printf '\xEF\xBB\xBF' > script_bom.ps1 && cat script.ps1 >> script_bom.ps1
   ```
   (or write it with an editor that adds a BOM). Then run:
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File "<path>\script_bom.ps1"
   ```
   It prints `OK: <locale>` per file and `ANCHOR NOT FOUND` if a file lacks the anchor key.
3. **Verify** with Grep (not by eye) that every locale got the keys and the non-ASCII text is intact:
   ```
   Grep pattern="<new_key_name>" glob="**/strings.xml" output_mode="content"
   ```
   Expect 11 hits with correct glyphs.
4. Build with `assembleDebug` so `mergeDebugResources` validates the XML (a stray `&`/unescaped quote
   or BOM fails the merge).

## Translations
Provide real translations for all 11 locales (don't leave English in non-English files). Keep
placeholders (`%1$d`, `%1$s`) identical across locales. Use the trailing ellipsis character `…` (not
three dots) to match existing in-progress strings.

## Maintaining this skill
Keep this SKILL.md (and the template script) current **deliberately, not automatically**. If the
locale set changes (a locale added/removed) or you find a better encoding-safe approach, update this
file + `insert-strings.template.ps1` in the same commit. Don't auto-rewrite on every use.

## Related memory
`strings-localization`, `build-env`.
