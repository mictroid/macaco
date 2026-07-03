# Macaco — ProfileScreen: Landscape Right Pane Stats Restructure (v4)

Moves the full stats card (Memories|Trips|Locations|Photos) from the left pane to the right
pane, and removes the redundant standalone "1 Trips" row that v3 added to the right pane.
Touches `ProfileScreen.kt` only.

Read `docs/DONE/code-brief-profile-landscape-v3.md` for the v3 layout that this builds on.

---

## Change 1 — Remove stats card from left pane

**Problem:** The stats Card (Memories | Trips | Locations | Photos) currently lives in the left
pane. The user wants it in the right pane instead.

**Fix:** Delete the entire stats `Card` block from the left pane Column. The `tripCount`
variable is already hoisted above the branch (v3), so no data changes are needed.

```kotlin
// REMOVE this block from the left pane Column (~line 481 of ProfileScreen.kt)
// Everything from the opening Card( to its closing } inclusive:

Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(
            value = "${entries.size}",
            label = stringResource(R.string.profile_memories)
        )
        if (tripCount > 0) {
            Box(
                modifier = Modifier
                    .width(1.dp).height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            StatItem(
                value = tripCount.toString(),
                label = stringResource(R.string.profile_trips)
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp).height(36.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatItem(
            value = entries.mapNotNull { it.location.ifBlank { null } }
                .distinct().size.toString(),
            label = stringResource(R.string.profile_locations)
        )
        Box(
            modifier = Modifier
                .width(1.dp).height(36.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        StatItem(
            value = entries.sumOf { it.photoUris.size }.toString(),
            label = stringResource(R.string.profile_photos)
        )
    }
}
```

---

## Change 2 — Rebuild right pane: add stats card, remove standalone Trips row

**Problem:** The right pane `Column` (centered in Box) has a standalone "1 Trips" row at the
top (tripCount > 0 guard) plus the action buttons. The Trips row is redundant once the full
stats card moves here. Replace it with the full stats card, keep the action buttons below.

**Fix:** In the right pane centered Column, replace the standalone `if (tripCount > 0) Row`
block with the full stats Card (same content as what was removed from the left pane).

```
RIGHT PANE — BEFORE              RIGHT PANE — AFTER
┌─────────────────────┐          ┌─────────────────────────────┐
│  ✈ 1 Trips          │          │  4 Memories│1 Trips│4 Loc│24│  ← stats card
│  [Subscription][Sign Out]│     │  [Subscription]   [Sign Out]│
│  Delete Account     │          │  Delete Account             │
└─────────────────────┘          └─────────────────────────────┘
```

```kotlin
// BEFORE — right pane Column content (~line 575 of ProfileScreen.kt)
Column(
    modifier = Modifier.align(Alignment.Center),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // Trips stat (only when the user has named trips)
    if (tripCount > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Flight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "$tripCount ${stringResource(R.string.profile_trips)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
    // Subscribe + Sign Out side-by-side
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = onSubscription,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(R.string.common_subscription),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = { showSignOutDialog = true },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                stringResource(R.string.common_sign_out),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    // Delete Account
    TextButton(
        onClick = { showDeleteAccountDialog = true },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text(
            stringResource(R.string.profile_delete_account),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// AFTER — replace the standalone Trips Row with the full stats Card
Column(
    modifier = Modifier.align(Alignment.Center),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    // Stats card — moved from left pane (v4)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                value = "${entries.size}",
                label = stringResource(R.string.profile_memories)
            )
            if (tripCount > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp).height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                StatItem(
                    value = tripCount.toString(),
                    label = stringResource(R.string.profile_trips)
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp).height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            StatItem(
                value = entries.mapNotNull { it.location.ifBlank { null } }
                    .distinct().size.toString(),
                label = stringResource(R.string.profile_locations)
            )
            Box(
                modifier = Modifier
                    .width(1.dp).height(36.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            StatItem(
                value = entries.sumOf { it.photoUris.size }.toString(),
                label = stringResource(R.string.profile_photos)
            )
        }
    }
    // Subscribe + Sign Out side-by-side (unchanged from v3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = onSubscription,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(R.string.common_subscription),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = { showSignOutDialog = true },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                stringResource(R.string.common_sign_out),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    // Delete Account (unchanged from v3)
    TextButton(
        onClick = { showDeleteAccountDialog = true },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        Text(
            stringResource(R.string.profile_delete_account),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
```

No new imports needed — `Card`, `CardDefaults`, `StatItem`, `Box`, `Row`, `Modifier.background`,
and all string resources are already in scope.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Remove stats Card from left pane landscape Column | `ProfileScreen.kt` |
| 2 | Replace standalone Trips row in right pane with full stats Card | `ProfileScreen.kt` |
