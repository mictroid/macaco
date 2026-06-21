# Macaco — New/Edit Entry: Keyboard Covers Tags When Typing Description

When the user taps the Description field and the keyboard rises, the Tags section below it is
hidden behind the keyboard with no way to see it without manually dismissing the keyboard first.
This brief fixes it with two complementary changes: move Description to the bottom of the form,
and add `imePadding()` to the `LazyColumn` as a safety net.
Touches: `NewEditEntryScreen.kt` only.

---

## 1. Reorder Description to the bottom of the form

**Problem:** Description sits above Tags/SuggestedTagsRow in the `LazyColumn`. When the keyboard
opens on the Description field, everything below it (Tags, SuggestedTagsRow) is hidden behind
the keyboard.

**Fix:** Move the Description `item {}` block and its hint row so it appears after the
SuggestedTagsRow, just before the trailing `Spacer`. This means when the keyboard is open on
the Description field there is nothing below it to miss — the keyboard sits naturally below the
content the user is filling in.

**Before (item order):**
```
┌───────────────────────────┐
│  Photos                   │
│  Title                    │
│  Location                 │
│  Trip                     │
│  Date                     │
│  Mood                     │
│  Description  ← focused   │  keyboard rises here
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│  ▓  Tags  (hidden)  ▓▓▓▓ │  ← user can't see this
│  ▓  SuggestedTags   ▓▓▓▓ │  ← or this
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
└───────────────────────────┘
```

**After (item order):**
```
┌───────────────────────────┐
│  Photos                   │
│  Title                    │
│  Location                 │
│  Trip                     │
│  Date                     │
│  Mood                     │
│  Tags                     │  ← visible before keyboard opens
│  SuggestedTagsRow         │  ← visible before keyboard opens
│  Description  ← focused   │  keyboard rises here — nothing below to miss
│  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
└───────────────────────────┘
```

**Implementation:** In the `LazyColumn` body inside `NewEditEntryScreen`, cut the two Description
items and paste them after the SuggestedTagsRow item, immediately before the trailing `Spacer`:

```kotlin
// BEFORE (current order, abridged):
LazyColumn(...) {
    item { /* Photos */ }
    item { /* Title */ }
    item { /* Location */ }
    item { /* Trip */ }
    item { /* Date */ }
    item { /* Mood */ }

    // Description — MOVE THIS BLOCK DOWN ↓
    item {
        OutlinedTextField(
            value = description,
            // ... full description field as-is, no changes to the field itself
        )
        if (description.isEmpty()) {
            HintRow(Icons.Filled.Mic, stringResource(R.string.new_entry_hint_story))
        }
    }

    // Tags
    item {
        SectionLabel(stringResource(R.string.new_entry_tags_label))
        Spacer(Modifier.height(8.dp))
        TagsField(tags = tags, onTagsChange = { tags = it }, suggestions = tagSuggestions)
        if (tags.isEmpty()) {
            HintRow(null, stringResource(R.string.new_entry_hint_tags))
        }
    }

    // Suggested tags
    item {
        SuggestedTagsRow(currentTags = tags, onAdd = { raw ->
            val tag = normalizeTag(raw)
            if (tag.isNotEmpty() && tag !in tags) tags = tags + tag
        })
    }

    item { Spacer(Modifier.height(24.dp)) }
}

// AFTER (correct order):
LazyColumn(...) {
    item { /* Photos */ }
    item { /* Title */ }
    item { /* Location */ }
    item { /* Trip */ }
    item { /* Date */ }
    item { /* Mood */ }

    // Tags (moved up)
    item {
        SectionLabel(stringResource(R.string.new_entry_tags_label))
        Spacer(Modifier.height(8.dp))
        TagsField(tags = tags, onTagsChange = { tags = it }, suggestions = tagSuggestions)
        if (tags.isEmpty()) {
            HintRow(null, stringResource(R.string.new_entry_hint_tags))
        }
    }

    // Suggested tags (moved up)
    item {
        SuggestedTagsRow(currentTags = tags, onAdd = { raw ->
            val tag = normalizeTag(raw)
            if (tag.isNotEmpty() && tag !in tags) tags = tags + tag
        })
    }

    // Description (moved to bottom — keyboard opens here with nothing below it)
    item {
        OutlinedTextField(
            value = description,
            // ... full description field unchanged
        )
        if (description.isEmpty()) {
            HintRow(Icons.Filled.Mic, stringResource(R.string.new_entry_hint_story))
        }
    }

    item { Spacer(Modifier.height(24.dp)) }
}
```

The Description field's own code is unchanged — only its position in the `LazyColumn` moves.

**File:** `ui/screens/NewEditEntryScreen.kt`

---

## 2. Add imePadding() to the LazyColumn

**Problem:** Even after reordering, if a user focuses Description on an already-scrolled-down
form, the `LazyColumn` doesn't shrink its scroll area when the keyboard appears, so the bottom
`Spacer` and last items can still be clipped. The `LazyColumn` needs to know the keyboard height.

**Fix:** Add `.imePadding()` to the `LazyColumn` modifier. `enableEdgeToEdge()` is already
called in `MainActivity`, so IME insets are properly exposed to Compose — this single modifier
is all that's needed. No manifest changes required.

```kotlin
// NewEditEntryScreen.kt — update the LazyColumn modifier:

// BEFORE:
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
)

// AFTER:
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .imePadding(),          // shrinks scroll area by keyboard height when keyboard is visible
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
)
```

Make sure `androidx.compose.foundation.layout.imePadding` is imported — it should already be
available since the file imports from `androidx.compose.foundation.layout.*`.

**File:** `ui/screens/NewEditEntryScreen.kt`

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Move Description item block below SuggestedTagsRow in `LazyColumn` | `ui/screens/NewEditEntryScreen.kt` |
| 2 | Add `.imePadding()` to `LazyColumn` modifier | `ui/screens/NewEditEntryScreen.kt` |
