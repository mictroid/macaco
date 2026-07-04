# Macaco — On This Day: Match Entry Card Width

The `OnThisDayBanner` card has `padding(horizontal = 16.dp)` in its own modifier. Since
`code-brief-journal-landscape-inset-scroll` moved the banner inside the `LazyColumn` as
item 0, and the `LazyColumn` already applies `contentPadding = PaddingValues(start = 16.dp,
end = 16.dp, ...)`, the banner now gets 32 dp of horizontal margin (16 dp from both sources)
while entry cards get only 16 dp from the list. The banner appears noticeably narrower.

Fix: remove `horizontal = 16.dp` from the banner's own modifier; let the LazyColumn content
padding provide the gutters, just as it does for all other items.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`

---

## Change — Remove horizontal padding from OnThisDayBanner card modifier (~line 1195)

### BEFORE
```kotlin
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
```

### AFTER
```kotlin
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
```

No other changes. The LazyColumn `contentPadding` of 16 dp (both portrait and landscape)
already keeps the card away from the screen edges, matching every entry card in the list.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove `horizontal = 16.dp` from `OnThisDayBanner` card modifier | `JournalListScreen.kt` |
