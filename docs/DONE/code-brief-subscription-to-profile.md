# Brief: Move Subscription from Drawer to Profile Screen

**Priority:** Medium  
**Files:**
- `app/src/main/java/com/houseofmmminq/macaco/ui/screens/JournalListScreen.kt`
- `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`
- `app/src/main/java/com/houseofmmminq/macaco/ui/navigation/NavGraph.kt`

## Rationale

The subscription `NavigationDrawerItem` in the sidebar is too visible — it constantly reminds users
of the paywall, which is the opposite of what we want. Better UX: tuck it in the Profile screen where
the user can find it when they actually want to manage their subscription, but it's not in their face
during normal journaling.

## Changes

### 1. Remove subscription item from JournalListScreen drawer

In `JournalListScreen.kt`, remove the `NavigationDrawerItem` for Subscription
(the one calling `onSubscription()`). Also remove the `onSubscription: () -> Unit` parameter from
the function signature and all call sites in NavGraph.

### 2. Add subscription row to ProfileScreen

In `ProfileScreen.kt`:

**a) Add parameter:**
```kotlin
fun ProfileScreen(
    ...existing params...,
    onSubscription: () -> Unit,
)
```

**b) Add a tappable row** in the profile body (place it logically near the sign-out section, e.g.
just above sign-out). The row should show a subscription/premium icon and the label "Manage
Subscription":

```kotlin
HorizontalDivider()
ListItem(
    headlineContent = { Text(stringResource(R.string.nav_subscription)) },
    leadingContent = {
        Icon(Icons.Outlined.Star, contentDescription = null)  // or suitable premium icon
    },
    modifier = Modifier.clickable { onSubscription() }
)
```

Use `Icons.Outlined.WorkspacePremium` if available in the extended icons set, otherwise
`Icons.Outlined.Star`.

### 3. Wire in NavGraph

In `NavGraph.kt`, update the `ProfileScreen` composable destination to pass:
```kotlin
onSubscription = { navController.navigate(Screen.Subscription.route) }
```

Ensure `Screen.Subscription` is still reachable from within the NavHost (the destination itself
doesn't move — only the entry point does).

## String resource

Reuse the existing `R.string.nav_subscription` string — no new strings needed.
