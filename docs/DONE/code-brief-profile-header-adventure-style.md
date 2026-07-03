# Macaco — Profile Header: Match Adventure Screen Style

The profile header (both portrait and landscape) should use the same icon size and "macaco"
title size as the adventure (map) screen header. Currently the portrait profile header has no
icon and a larger macaco text (24 sp); the landscape header has a 24 dp gold-tinted icon and
16 sp text. The adventure screen uses: portrait = 44 dp natural-colour icon + 20 sp macaco;
landscape = 20 dp natural-colour icon + 14 sp macaco.

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/ProfileScreen.kt`

---

## Change 1 — Portrait header: add icon, match adventure text sizes (~line 680)

The portrait header's centred Column currently has only text. Add the icon above "macaco"
and match the adventure portrait sizes exactly.

### BEFORE
```kotlin
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(top = 18.dp, bottom = 60.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 24.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 6.sp
    )
    Text(
        text = "Roam Freely. Forget Nothing.",
        color = SplashGold.copy(alpha = 0.82f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 1.sp
    )
}
```

### AFTER
```kotlin
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(top = 8.dp, bottom = 60.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(44.dp)
            .offset(y = 4.dp)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 20.sp,
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
}
```

Notes:
- Icon has **no `colorFilter`** — same as adventure portrait (natural icon colours).
- `top` padding reduced 18 dp → 8 dp because the 44 dp icon adds visual height; `bottom = 60.dp`
  unchanged so the avatar overlap stays correct.
- `offset(y = 4.dp)` matches adventure exactly, nudging the icon down into the text.

---

## Change 2 — Landscape header: match adventure landscape icon size, remove gold tint (~line 341)

The landscape compact header Column currently has a 24 dp gold-tinted icon. Change to 20 dp
with natural colours (no `colorFilter`) to match the adventure landscape header.

### BEFORE
```kotlin
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = ColorFilter.tint(SplashGoldBright)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 16.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 4.sp
    )
}
```

### AFTER
```kotlin
Column(
    modifier = Modifier
        .align(Alignment.Center)
        .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(20.dp)
            .offset(y = (-2).dp)
    )
    Text(
        text = "macaco",
        color = SplashGoldBright,
        fontSize = 14.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 3.sp
    )
}
```

Notes:
- Icon **20 dp** (down from 24 dp) and **no `colorFilter`** — matches adventure landscape Row.
- `offset(y = (-2).dp)` matches the adventure landscape icon offset.
- macaco **14 sp / letterSpacing 3 sp** — matches adventure landscape text.
- If `ColorFilter` becomes unused after removing the import, remove the `import
  androidx.compose.ui.graphics.ColorFilter` line too.
