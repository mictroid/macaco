# Macaco — ProfileScreen: Landscape two-pane layout

One file: `ui/screens/ProfileScreen.kt`.

---

## Background

On a phone in landscape (A53: ~412dp tall), the ProfileScreen's fixed-height elements
— action buttons (~180dp) + branded footer (80dp) — consume ~260dp of the ~412dp screen.
The remaining ~150dp scroll area has to contain the tall banner (banner box with 60dp bottom
padding + status bar) plus the avatar, name, email, stats card, etc. The result: the user
sees only the action buttons and footer; all the profile identity content is scrolled off.

**Fix:** In landscape, replace the current `Column { scroll area + buttons + footer }` with a
two-pane `Row` — identity info left (50%), action buttons right (50%). Portrait path is
untouched.

---

## Layout diagram (landscape)

```
┌────────────────────────┬────────────────────────┐
│ [←]       macaco       │                        │
│ ─────────────────────  │   💎 Subscription      │
│                        │                        │
│  ┌──[Avatar 80dp]──┐   │   ↩  Sign Out          │
│  │    Michael      │   │                        │
│  │  michael@...    │   │  🗑 Delete Account      │
│  │  🔵 Google      │   │                        │
│  └─────────────────┘   │         [🐒]           │
│   ┌──Stats card──┐     │                        │
│   │ 12 | 3 |47|24│     │                        │
│   └──────────────┘     │                        │
│   Member since Jun 2025│                        │
└────────────────────────┴────────────────────────┘
   scrollable if needed     buttons centred vertically
                            logo pinned to bottom
```

---

## Fix — Add landscape two-pane layout

### Step 1 — Detect landscape (add near top of composable, below existing `val context`)

```kotlin
// BEFORE — no landscape detection in ProfileScreen

// AFTER — add these two lines
val configuration = LocalConfiguration.current
val isLandscape = configuration.screenHeightDp < 480
```

Import if not present: `import androidx.compose.ui.platform.LocalConfiguration`

---

### Step 2 — Replace Scaffold body with a landscape/portrait branch

The current Scaffold body (lines ~265–628) is a single `Column`. Wrap it in an `if/else`
so landscape gets the new two-pane layout and portrait keeps the existing code verbatim.

```kotlin
// BEFORE — Scaffold body (line ~265)
Scaffold(...) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                // ... all existing portrait content ...
```

```kotlin
// AFTER — add landscape branch before the existing Column
Scaffold(...) { padding ->
    if (isLandscape) {
        // ── LANDSCAPE: two-pane Row ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // LEFT PANE — compact banner + identity info (scrollable)
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Compact banner — back arrow left, "macaco" centred, no tagline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(macacoBrandBackground())
                        .statusBarsPadding()
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "macaco",
                        color = SplashGoldBright,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 5.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 12.dp)
                    )
                }

                // Identity content — no offset trick needed in landscape
                val user = currentUser
                if (user != null) {
                    Spacer(Modifier.height(16.dp))

                    // Avatar — slightly smaller in landscape (80dp vs 100dp)
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .clickable { showPhotoSourceSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val displayPhotoModel: Any? = profilePhotoUri ?: user.photoUrl
                            if (displayPhotoModel != null) {
                                AsyncImage(
                                    model = displayPhotoModel,
                                    contentDescription = stringResource(R.string.profile_photo_cd),
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = rememberVectorPainter(Icons.Default.Person)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        user.displayName.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            // Camera badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    contentDescription = stringResource(R.string.profile_change_photo_cd),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = when (user.provider) {
                                AuthProvider.Google -> stringResource(R.string.profile_google_account)
                                AuthProvider.Email  -> stringResource(R.string.profile_email_account)
                                AuthProvider.Guest  -> stringResource(R.string.profile_guest)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Stats card (same logic as portrait)
                    val tripCount = entries
                        .mapNotNull { it.tripName?.trim()?.ifBlank { null } }
                        .distinct()
                        .size
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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

                    val memberSince = user.createdAt?.let { millis ->
                        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(millis))
                    }
                    if (memberSince != null) {
                        Text(
                            text = "Member since $memberSince",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    // Not signed in — centred sign-in prompt
                    Spacer(Modifier.height(32.dp))
                    Text("🔑", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.profile_no_account_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.profile_no_account_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } // end LEFT PANE

            // RIGHT PANE — action buttons centred vertically, macaco logo pinned to bottom
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp)
            ) {
                if (currentUser != null) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = onSubscription,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.WorkspacePremium,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.common_subscription))
                        }
                        OutlinedButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.common_sign_out))
                        }
                        TextButton(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.profile_delete_account))
                        }
                    }
                } else {
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.common_sign_in), fontWeight = FontWeight.SemiBold)
                    }
                }
                // Macaco logo pinned to bottom of right pane
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            } // end RIGHT PANE
        } // end landscape Row

    } else {
        // ── PORTRAIT: existing layout — DO NOT MODIFY ────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ... existing portrait Column content verbatim (lines ~271–628) ...
        }
    }
} // end Scaffold
```

---

## Scope

- **In:** Landscape ProfileScreen two-pane layout — compact banner left, avatar/name/stats
  left (scrollable), action buttons right, macaco logo right-bottom.
- **Out:** Portrait ProfileScreen — zero changes, existing code verbatim.
- **Out:** ProfileScreen dialogs (sign-out confirm, delete account confirm, photo source sheet)
  — these are defined above the Scaffold and remain unchanged in both orientations.
- **Out:** Landscape detection for other screens — handled in their own briefs.

---

## Verification

On A53 in landscape:
1. Profile info (avatar, name, email, provider chip, stats) fills the left pane — no scrolling
   needed for a typical account.
2. Action buttons (Subscription, Sign Out, Delete Account) are centred vertically in the right
   pane — all visible without scrolling.
3. Macaco logo appears at the bottom of the right pane.
4. Tapping avatar still opens the photo source sheet.
5. Tapping Sign Out / Delete Account still shows the confirmation dialogs.
6. Rotating back to portrait: existing layout unchanged.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `isLandscape` detection | `ProfileScreen.kt` |
| 2 | Landscape two-pane Row: identity info left, action buttons right | `ProfileScreen.kt` |
