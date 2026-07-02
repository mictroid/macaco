# Macaco — JournalListScreen: landscape header centering + drawer landscape compact

One file: `ui/screens/JournalListScreen.kt`.

Two separate layout fixes both in JournalListScreen: (1) the landscape header brand content
is left-aligned instead of centred; (2) the modal drawer has a tall portrait header that
overflows in landscape, pushing nav items off-screen.

---

## Fix 1 — Centre brand content in landscape header

**Problem:** The landscape header Row is:
```
[hamburger] [icon 24dp] [Spacer 6dp] ["macaco"] [memories text] [Spacer weight(1f)] [avatar]
```
The `Spacer(weight(1f))` pushes the avatar to the far right, but all brand content sits at
the left edge. The "macaco" logo and wordmark appear left-aligned, not centred on the
brand header.

**Fix:** Wrap the brand content in a `Box(Modifier.weight(1f))` so it fills the space
between the hamburger and the avatar, with the inner content centred. Use equal-width
spacers or a fixed-size placeholder on both sides so the centre is truly symmetric.

```
Before:
┌──────────────────────────────────────────────────┐
│ ☰  🐒 macaco · 12 memories          [avatar]    │
│ ↑ brand content left-aligned                      │
└──────────────────────────────────────────────────┘

After:
┌──────────────────────────────────────────────────┐
│ ☰           🐒 macaco · 12 memories     [avatar] │
│ 40dp │←──────── centred in remaining space ──────→│ ~40dp │
└──────────────────────────────────────────────────┘
```

```kotlin
// BEFORE (line ~438 in JournalListScreen.kt, landscape branch of the topBar Box)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(
        onClick = { scope.launch { drawerState.open() } },
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = stringResource(R.string.journal_list_menu_cd),
            tint = Color.White
        )
    }
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
    Spacer(Modifier.width(6.dp))
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 14.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 3.sp
    )
    if (entries.isNotEmpty()) {
        val count = visibleEntries.size
        val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
        Text(
            text = " · " + memoriesText +
                if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = SplashGold.copy(alpha = 0.7f)
        )
    }
    Spacer(Modifier.weight(1f))
    if (currentUser != null) {
        // ... avatar ...
    }
}

// AFTER — brand content centred between fixed-width flanking elements
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    // Left anchor: hamburger menu — fixed 40dp so centre is truly symmetric
    IconButton(
        onClick = { scope.launch { drawerState.open() } },
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            Icons.Filled.Menu,
            contentDescription = stringResource(R.string.journal_list_menu_cd),
            tint = Color.White
        )
    }

    // Brand content fills the remaining space, centred
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .offset(y = (-2).dp)   // visual alignment — same fix as MapScreen
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp
            )
            if (entries.isNotEmpty()) {
                val count = visibleEntries.size
                val memoriesText = pluralStringResource(R.plurals.journal_list_memories, count, count)
                Text(
                    text = " · " + memoriesText +
                        if (selectedTags.isNotEmpty()) " · ${stringResource(R.string.journal_list_filtered)}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = SplashGold.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Right anchor: avatar — same visual weight as the hamburger so centre stays symmetric.
    // Use a 40dp-wide Box to match the hamburger width even when no avatar is present.
    Box(
        modifier = Modifier
            .size(40.dp)
            .padding(end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (currentUser != null) {
            if (profilePhotoUri != null) {
                AsyncImage(
                    model = profilePhotoUri,
                    contentDescription = stringResource(R.string.common_profile),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onProfile() },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        currentUser!!.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SplashTealMid
                    )
                }
            }
        }
    }
}
```

**File:** `ui/screens/JournalListScreen.kt`

---

## Fix 2 — Compact drawer header in landscape

**Problem:** The `ModalDrawerSheet` header always uses the full portrait layout: 64dp icon +
wordmark + slogan + avatar + user name. On A53 in landscape (screen height ≈ 360dp), this
header alone is ~160dp, leaving only ~200dp for 6+ nav items, a spacer, and sign-out. Items
at the bottom (sign-out) get pushed below the visible drawer area.

**Fix:** Detect landscape inside the drawer and use a compact horizontal header that
matches the pattern used by the landscape app headers elsewhere.

```
Portrait drawer header (unchanged in portrait):
┌──────────────────────┐
│  [monkey 64dp]       │
│  macaco              │
│  Roam Freely.        │
│  [avatar 44dp]       │
│  User Name           │
└──────────────────────┘  ~160dp tall

Landscape drawer header (new):
┌──────────────────────┐
│  [icon 32dp] macaco · User Name  [avatar 32dp] │
└──────────────────────┘  ~48dp tall
```

```kotlin
// BEFORE (line ~226) — ModalDrawerSheet content, the header Box
ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.surface,
    windowInsets = WindowInsets(0)
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(macacoBrandBackground())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Column(
                modifier = Modifier.offset(y = (-8).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("macaco", color = SplashGoldBright, fontSize = 22.sp, ...)
                Text("Roam Freely. Forget Nothing.", ...)
                Spacer(Modifier.height(8.dp))
                // avatar + name ...
            }
        }
    }
    // nav items follow ...
}

// AFTER — same content but landscape-aware header
val drawerIsLandscape = LocalConfiguration.current.screenHeightDp < 480

ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.surface,
    windowInsets = WindowInsets(0)
) {
    if (drawerIsLandscape) {
        // Compact horizontal header for landscape — avoids consuming too much vertical space
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .offset(y = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "macaco",
                color = SplashGoldBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
            if (currentUser != null) {
                Text(
                    text = " · " + currentUser!!.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.weight(1f))
            if (currentUser != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            scope.launch { drawerState.close() }
                            onProfile()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePhotoUri != null) {
                        AsyncImage(
                            model = profilePhotoUri,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            currentUser!!.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    } else {
        // Portrait: existing header — move the existing portrait Box here verbatim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(macacoBrandBackground())
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
                Column(
                    modifier = Modifier.offset(y = (-8).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 5.sp
                    )
                    Text(
                        text = "Roam Freely. Forget Nothing.",
                        color = SplashGold.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (currentUser != null) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onProfile()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profilePhotoUri != null) {
                                AsyncImage(
                                    model = profilePhotoUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    currentUser!!.displayName.take(2).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = if (currentUser != null) currentUser!!.displayName else "Not signed in",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = if (currentUser != null) Modifier.clickable {
                            scope.launch { drawerState.close() }
                            onProfile()
                        } else Modifier
                    )
                }
            }
        }
    }

    // Nav items (Spacer + NavigationDrawerItem rows) are unchanged — leave them as-is
    Spacer(Modifier.height(8.dp))
    // ... existing nav items ...
}
```

**Note:** `drawerIsLandscape` needs `LocalConfiguration.current` — make sure
`LocalConfiguration` is imported and in scope at the point where `ModalDrawerSheet` is
composed. The `currentUser`, `profilePhotoUri`, `scope`, `drawerState`, and `onProfile`
are already in scope in `JournalListScreen`.

**File:** `ui/screens/JournalListScreen.kt`

---

## Scope

- **In:** Landscape header brand centering; compact landscape drawer header.
- **Out:** Portrait header — unchanged.
- **Out:** Nav items inside the drawer — unchanged (only the header changes).
- **Out:** FAB, entry list, search bar, tag filter — no changes.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Landscape header: brand content centred with symmetric 40dp flanking elements | `JournalListScreen.kt` |
| 2 | Drawer header: landscape-aware compact Row instead of portrait tall Column | `JournalListScreen.kt` |
