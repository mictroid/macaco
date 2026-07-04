# Macaco — Revert On This Day & Trip Header to primaryContainer

`code-brief-primary-container-tint` changed `OnThisDayBanner` and `TripHeader` from
`primaryContainer` to `primary.copy(alpha = 0.12f)` to make them feel closer to the
theme swatch. User feedback: the result is too grey/muted and no longer reads as the
theme colour. Revert both to `primaryContainer`.

`primaryContainer` in light mode is indeed lighter (e.g. mint in Forest), but it is the
correct M3 surface token for tinted containers — it is fully intentional that it is a
pale tint, not the full `primary` colour. The user prefers this behaviour.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change 1 — OnThisDayBanner card background (~line 1198)

### BEFORE
```kotlin
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
```

### AFTER
```kotlin
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
```

---

## Change 2 — TripHeader background (~line 1351)

### BEFORE
```kotlin
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
```

### AFTER
```kotlin
            .background(MaterialTheme.colorScheme.primaryContainer)
```

No other changes. Both `onPrimaryContainer` text/icon colour references in these composables
remain correct for `primaryContainer` as the background.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `OnThisDayBanner` containerColor: `primary.copy(alpha=0.12f)` → `primaryContainer` | `JournalListScreen.kt` |
| 2 | `TripHeader` background: `primary.copy(alpha=0.12f)` → `primaryContainer` | `JournalListScreen.kt` |
