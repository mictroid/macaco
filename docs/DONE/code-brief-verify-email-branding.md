# Macaco — Verify Email Screen: Branding & Copy

Personalizes `VerifyEmailScreen.kt`, replacing the generic Material email icon with the Macaco
brand mark and warming up the copy in `strings.xml` (all 11 languages).

---

## Brand mark + wordmark instead of generic email icon

**Problem:** `VerifyEmailScreen.kt` uses a stock `Icons.Filled.Email` Material glyph. Nothing on
this screen signals it's Macaco — it reads like a generic auth template.

An earlier version of this brief specified `ic_macaco_small.xml` at 80dp, but that drawable is
purpose-built to stay legible when shrunk down to ~24dp (see its own file comment: ears, inner
ears, muzzle, eyebrows and nostrils are deliberately omitted). Enlarged to 80dp it under-renders —
just a thin ring and two dots, not recognizably a monkey. Use the full detail mark instead:
`ic_launcher_foreground.xml` (ears, muzzle, eyebrows, nostrils all present), which is the same
drawable already used at 220dp on `SplashScreen.kt`. Pair it with the "macaco" wordmark, mirroring
the icon+wordmark pattern from the splash screen so this screen reinforces the same brand moment
instead of introducing a new one — just toned down for a plain light `Scaffold` rather than the
splash's full-bleed teal gradient treatment.

**Fix:** Swap the `Icon` composable for an `Image` rendering `R.drawable.ic_launcher_foreground`
at 96dp, followed by a small "macaco" wordmark `Text` in `primary` color (matches the button),
light weight, letter-spaced — a subdued echo of the splash screen's treatment, not a copy of its
44sp gold-on-teal version.

```kotlin
// BEFORE (VerifyEmailScreen.kt, lines 46–51)
            Icon(
                Icons.Filled.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

// AFTER
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "macaco",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
```

Add imports:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
```

(Remove the now-unused `androidx.compose.material.icons.Icons` / `filled.Email` imports if nothing
else in the file uses them.)

**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/VerifyEmailScreen.kt`

---

## Warmer copy

**Problem:** Current copy is functional but generic — "Verify your email" / "We sent a
verification link to X. Click it, then come back here." Doesn't sound like Macaco next to the
rest of the app's voice (see onboarding/Help & About for tone reference: friendly, direct, a
little playful).

**Fix:** Update the title and subtitle strings. Keep the `%1$s` placeholder in the subtitle (email
address) and the resend-cooldown format placeholder unchanged — only the title and subtitle
wording change.

| Key | Old EN value | New EN value |
|-----|-----|-----|
| `verify_email_title` | Verify your email | Almost there |
| `verify_email_subtitle` | We sent a verification link to %1$s. Click it, then come back here. | We sent a link to %1$s — tap it to unlock your journal. |

Leave `verify_email_continue`, `verify_email_still_unverified`, `verify_email_resend`,
`verify_email_resend_cooldown`, and `verify_email_sign_out` as-is; they're already plain and
functional and don't need a tone pass.

**File:** `app/src/main/res/values/strings.xml` (lines 264–265), plus the same two keys in all 10
localized `values-*/strings.xml` files (`values-de`, `values-es`, `values-fr`, `values-it`,
`values-nl`, `values-pl`, `values-pt`, `values-sv`, `values-ja`, `values-zh-rCN`) — translate the
new EN wording into each language, matching that language's existing tone in nearby strings (e.g.
`help_faq_verify_email_a`).

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Swap generic email icon for full Macaco brand mark + "macaco" wordmark (`ic_launcher_foreground`, 96dp) | `VerifyEmailScreen.kt` |
| 2 | Warmer title/subtitle copy | `strings.xml` × 11 languages |
