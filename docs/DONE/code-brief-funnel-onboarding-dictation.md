# Macaco — Conversion Release: Funnel Analytics, Honest Onboarding, Soft Paywall, Premium Badges, Dictation & Capitalization

One release covering six things: (A) Firebase funnel events, (B) honest + localized onboarding
copy, (C) a one-time soft paywall after the first saved entry, (D) "Premium" badges on locked
features, (E) better dictation and proper sentence capitalization, (F) localized release notes.
Touches `util/Analytics.kt` (new), `TravelJournalApp.kt`, `JournalViewModel.kt`, `NavGraph.kt`,
`Screen.kt`, `PurchaseScreen.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`, `ProfileScreen.kt`,
`NewEditEntryScreen.kt`, `PreferencesManager.kt`, `strings.xml` ×11, and
`app/src/main/play/release-notes/`.

---

## Why (context Code doesn't have)

RevenueCat data, 1 May – 22 Nov 2026: 619 new customers, **2 paid** (both annual, via the trial).
Most of those users were Reddit testers before the Production launch on 22 Jul; since August, organic
arrivals are only 5–25 a month. Right now **we can't see the funnel at all**: Firebase Analytics is
in the app (BOM + `firebase-analytics`) but **no custom event is logged anywhere** (grep for
`logEvent` returns nothing). RevenueCat can't see paywall views either, because the paywall is our
own Compose screen. We need to know whether people (a) never write enough entries to hit the paywall,
(b) see it and leave, or (c) start checkout and cancel. Separately, the onboarding promises
*"One purchase — lifetime access, no subscription"*, but the paywall leads with an **annual
subscription**. That's a trust-breaking mismatch at checkout.

Store-side changes (title, Data safety, screenshots) already shipped via Play Console. **Data safety
now declares "App interactions — Analytics"**, so the events below are covered. Don't add new data
types (no ad ID, no location, no free-text content in event params).

---

## A. Funnel analytics

**Problem:** zero visibility between install and purchase.

**Fix:** a tiny wrapper around `FirebaseAnalytics`, set up once in `TravelJournalApp`, and called at
each funnel step. Event and param names are lowercase snake_case (Firebase limits: ≤40-char names,
≤100-char string values). **Never put entry text, titles, locations, emails or names in params.**

```kotlin
// NEW FILE — util/Analytics.kt
package com.houseofmmminq.macaco.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/** Thin funnel-event wrapper. Params must never contain user content or PII. */
object Analytics {
    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun log(event: String, vararg params: Pair<String, Any?>) {
        val b = Bundle()
        params.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is String -> b.putString(k, v.take(100))
                is Int -> b.putLong(k, v.toLong())
                is Long -> b.putLong(k, v)
                is Boolean -> b.putString(k, v.toString())
                else -> b.putString(k, v.toString().take(100))
            }
        }
        fa?.logEvent(event, b)
    }

    fun setPremium(isPremium: Boolean) {
        fa?.setUserProperty("is_premium", isPremium.toString())
    }
}
```

```kotlin
// TravelJournalApp.kt — in onCreate(), after super.onCreate()
Analytics.init(this)
```

**Events to add:**

| Event | Where | Params |
|---|---|---|
| `onboarding_complete` | `JournalViewModel.completeOnboarding()` | — |
| `entry_created` | `JournalViewModel.saveEntry()`, **only when `entry.id` isn't already in `entries.value`** (new, not edit) | `entry_number` (count after save), `has_photos`, `has_video`, `used_dictation` (see E), `is_premium` |
| `paywall_view` | `PurchaseScreen`, in a `LaunchedEffect(Unit)` | `source` (see the route change below) |
| `paywall_dismissed` | `PurchaseScreen` back/close, without a purchase | `source` |
| `plan_selected` | the three `selectedPlan = …` handlers (lines ~225/239/253) | `plan` = `annual` / `monthly` / `lifetime` |
| `purchase_started` | right before `viewModel.purchase(act, pkg)` (~line 282) | `plan`, `source` |
| `purchase_completed` / `purchase_cancelled` / `purchase_failed` | the `viewModel.purchase` result callback: success+true → completed, success+false → cancelled (RevenueCat `userCancelled`), failure → failed | `plan`, `source`; failed also `reason` = exception class simple name only |
| `dictation_used` | speech launcher result in `NewEditEntryScreen` | `result` = `ok` / `empty` / `cancelled` |

Also call `Analytics.setPremium(it == true)` wherever `isPremium` changes. Collecting
`billingManager.isPremium` in `JournalViewModel.init` is the simplest place.

**Paywall source:** today `Screen.Paywall` is the bare route `"paywall"`, so we can't tell entry
points apart. Add an optional arg:

```kotlin
// Screen.kt — BEFORE
object Paywall : Screen("paywall")

// AFTER
object Paywall : Screen("paywall?source={source}") {
    fun createRoute(source: String) = "paywall?source=$source"
}
```

```kotlin
// NavGraph.kt — composable(Screen.Paywall.route) becomes:
composable(
    route = Screen.Paywall.route,
    arguments = listOf(navArgument("source") { type = NavType.StringType; defaultValue = "unknown" })
) { backStackEntry ->
    val source = backStackEntry.arguments?.getString("source") ?: "unknown"
    LaunchedEffect(isPurchased) { if (isPurchased == true) navController.popBackStack() }
    PurchaseScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
        showFreeLimitNote = source == "entry_limit",
        source = source,               // NEW param, used for the analytics events
    )
}
```

Source values (use exactly these):

| Call site | `source` |
|---|---|
| `goToNewEntry` (NavGraph ~180, free limit hit) | `entry_limit` |
| Settings → Drive backup connect | `drive_backup` |
| Settings → backup export/import | `file_backup` |
| Settings → Print Book | `print_book` |
| Profile → subscription tiles (~600/637) | `profile` |
| Subscription info → onUpgrade (~395) | `subscription_info` |
| Soft paywall (section C) | `first_entry` |
| Any other existing caller | add a descriptive value; don't leave it `unknown` |

`SettingsScreen`/`ProfileScreen` currently take `onNavigateToPaywall: () -> Unit`. Change them to
`(source: String) -> Unit` and pass the value at each call site. `showFreeLimitNote` used to be
always `true`. It now shows only for `entry_limit`, where it actually makes sense.

**Verify:** run with `adb shell setprop debug.firebase.analytics.app com.houseofmmminq.macaco` and
check Firebase Console → DebugView to confirm each event fires once with the right params (no
duplicate `paywall_view` on recomposition).

Files: `util/Analytics.kt` (new), `TravelJournalApp.kt`, `JournalViewModel.kt`, `Screen.kt`,
`NavGraph.kt`, `PurchaseScreen.kt`, `SettingsScreen.kt`, `ProfileScreen.kt`, `NewEditEntryScreen.kt`

---

## B. Honest, localized onboarding copy

**Problem:** `OnboardingScreen.kt` slide 2 says *"No ads. No social feed. One purchase — lifetime
access, no subscription."* That's wrong, because the paywall leads with an annual subscription (with
monthly and lifetime as options). Also, **every onboarding string is a hardcoded English literal**
(slides 1–3 plus "Roam Freely. Forget Nothing."), so German, Portuguese and the other 8 locales see
English onboarding.

**Fix:** move every onboarding literal into `strings.xml` and rewrite slide 2 to describe the real
freemium model. Slide 2 is still about privacy, with a neutral line about pricing:

```kotlin
// OnboardingScreen.kt — BEFORE (slide 2)
2 -> OnboardingSlide(
    icon = Icons.Outlined.Shield,
    title = "Private.\nYours. Forever.",
    body = "No ads. No social feed. One purchase — lifetime access, no subscription.",
    compact = isLandscape
)

// AFTER
2 -> OnboardingSlide(
    icon = Icons.Outlined.Shield,
    title = stringResource(R.string.onboarding_private_title),
    body = stringResource(R.string.onboarding_private_body),
    compact = isLandscape
)
```

Do the same for slides 1 and 3 and for the tagline Text. Keep the visual design as it is.

| Key | EN value |
|-----|----------|
| `onboarding_tagline` | Roam Freely. Forget Nothing. |
| `onboarding_sync_title` | Your journal,\nalways with you |
| `onboarding_sync_body` | Every memory syncs to the cloud instantly. Write on your phone, relive anywhere. |
| `onboarding_private_title` | Private.\nYours. Forever. |
| `onboarding_private_body` | No ads, no social feed, and your journal is never sold. Start free, and unlock Premium whenever you're ready. |
| `onboarding_ready_title` | Ready to roam? |
| `onboarding_ready_body` | Sign in to start capturing your adventures. |

All 11 locales (`values`, `-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`, `-sv`, `-zh-rCN`)
need native translations. Use the `bulk-localize-strings` skill. The German, Portuguese and
Spanish versions especially should read naturally, since those are the markets we're pushing now.

Files: `OnboardingScreen.kt`, `strings.xml` ×11

---

## C. One-time soft paywall after the first saved entry

**Problem:** the only point where a free user is *pushed* to the paywall is trying to create entry
#4. Many travellers write one or two entries and never get there, so they never see what Premium
offers. (We can't show a purchase screen *during* onboarding, because onboarding runs before sign-in
and RevenueCat purchases are tied to the signed-in `uid`.)

**Fix:** right after a non-premium user saves their **very first new entry**, show the paywall once,
with `source = "first_entry"`. It's dismissible as normal and never shown again.

```kotlin
// PreferencesManager.kt — new key + accessors
private val KEY_SOFT_PAYWALL_SHOWN = booleanPreferencesKey("soft_paywall_shown")
val softPaywallShown: Flow<Boolean> = context.dataStore.data.map { it[KEY_SOFT_PAYWALL_SHOWN] ?: false }
suspend fun setSoftPaywallShown() { context.dataStore.edit { it[KEY_SOFT_PAYWALL_SHOWN] = true } }
```

Expose `softPaywallShown` as a `StateFlow<Boolean?>` on `JournalViewModel` with a
`markSoftPaywallShown()` function.

```kotlin
// NavGraph.kt — composable(Screen.NewEntry.route) — BEFORE
onSave = { entry ->
    viewModel.saveEntry(entry)
    navController.popBackStack()
},

// AFTER
onSave = { entry ->
    val isFirstEntry = entries.isEmpty()                 // `entries` is already collected in this block
    viewModel.saveEntry(entry)
    navController.popBackStack()
    if (isFirstEntry && isPurchased == false && softPaywallShown == false) {
        viewModel.markSoftPaywallShown()
        navController.navigate(Screen.Paywall.createRoute("first_entry"))
    }
},
```

Only `isPurchased == false` triggers it (null/loading never does), and it only fires from the
*create* flow, never edit. Existing users who already have entries never see it, because
`entries.isEmpty()` is false. Add a short heading above the plans when `source == "first_entry"`:

| Key | EN value |
|-----|----------|
| `paywall_first_entry_heading` | Great start! Keep every trip with Premium |
| `paywall_first_entry_sub` | Try it free for 7 days. You can keep journaling for free either way. |

Only show the "7 days" line when `annualTrialDays != null`. Reuse the live trial-days value
`PurchaseScreen` already reads; never hardcode the 7. If there's no trial, use a variant without the
trial clause (`paywall_first_entry_sub_no_trial`: "You can keep journaling for free either way.").

Files: `PreferencesManager.kt`, `JournalViewModel.kt`, `NavGraph.kt`, `PurchaseScreen.kt`,
`strings.xml` ×11

---

## D. "Premium" badges on locked features

**Problem:** free users can't tell which features are premium until they tap one and land on the
paywall. Settings shows the Print Book row as `print_premium_required` text, but Drive backup and
file backup don't show anything up front.

**Fix:** a small reusable badge, shown next to the title of each locked feature **only when
`isPurchased == false`**. Use M3 tokens only, no hardcoded colours (the app ships 7 themes).

```kotlin
// NEW — ui/components/PremiumBadge.kt
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, modifier = Modifier.size(12.dp))
            Text(stringResource(R.string.premium_badge), style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

```
┌──────────────────────────────────────────────┐
│ ☁  Google Drive backup   [♛ Premium]          │
│    Back up photos & videos to your Drive      │
└──────────────────────────────────────────────┘
```

Place it on: the Drive backup card title, the Backup & restore card title, the Print Book row title,
the Adventure Reel action (`JournalListScreen` ~1389), and the Year in Travel share action if it's
gated. Check each feature's actual gate before adding a badge; don't badge free features (the map
and entry cloud sync are free).

| Key | EN value |
|-----|----------|
| `premium_badge` | Premium |

Files: `ui/components/PremiumBadge.kt` (new), `SettingsScreen.kt`, `JournalListScreen.kt`,
`strings.xml` ×11

---

## E. Dictation quality + proper sentence capitalization

**Problem:**
1. None of the free-text fields set `KeyboardCapitalization`, so many keyboards don't capitalize the
   first letter of a sentence in the story or the title.
2. Dictation appends the raw recognizer string: `"$description $text"`. Google's recognizer often
   returns lowercase text without end punctuation, so a second dictation runs into the first
   ("…sunset we ate pasteis de nata") and starts in lowercase.
3. Dictation always appends at the **end**, even if the user placed the cursor mid-text.
4. On Android 13+ the recognizer can add punctuation and capitalization itself, but we don't ask
   for it.

**Fix:**

**E1. Keyboard capitalization:**

| Field | `keyboardOptions` |
|---|---|
| Title (~810) | `KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)` |
| Location (`LocationField`, ~1175) | `KeyboardOptions(capitalization = KeyboardCapitalization.Words)` |
| Trip (`TripField`, ~1240) | `KeyboardOptions(capitalization = KeyboardCapitalization.Words)` |
| Description / story (~925) | `KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)` |
| Tags (~1073) | unchanged (`imeAction = Done`); tags stay lowercase |
| Custom mood emoji dialog | unchanged |

Import `androidx.compose.ui.text.input.KeyboardCapitalization`. If a field already sets
`keyboardOptions`, merge into the existing object; don't replace it.

**E2. Ask the recognizer for formatting (API 33+):**

```kotlin
// NewEditEntryScreen.kt — inside the Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { … } block, ADD:
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
}
putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
```

Keep the existing `EXTRA_LANGUAGE` / `EXTRA_LANGUAGE_PREFERENCE` (in-app locale) exactly as they are.

**E3. Insert at the cursor, with sentence-aware joining.** Switch the description state from `String`
to `TextFieldValue` so we know the cursor position. Save still writes `description.text`.

```kotlin
// BEFORE
var description by rememberSaveable { mutableStateOf(existingEntry?.description ?: "") }
…
if (!text.isNullOrEmpty()) {
    description = if (description.isBlank()) text else "$description $text"
}

// AFTER
var description by rememberSaveable(stateSaver = TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(existingEntry?.description ?: ""))
}
…
if (!text.isNullOrEmpty()) {
    description = DictationText.insert(description, text)
    usedDictation = true          // rememberSaveable Boolean, feeds entry_created.used_dictation
    Analytics.log("dictation_used", "result" to "ok")
} else if (result.resultCode == Activity.RESULT_OK) {
    Analytics.log("dictation_used", "result" to "empty")
} else {
    Analytics.log("dictation_used", "result" to "cancelled")
}
```

Update every other reader or writer of `description` (`OutlinedTextField` value/onValueChange,
`description.isEmpty()` hint check, the save at ~610 `description = description.trim()` becomes
`description.text.trim()`, and any seed/prefill code) accordingly.

```kotlin
// NEW — util/DictationText.kt (pure Kotlin, so it's unit-testable)
package com.houseofmmminq.macaco.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object DictationText {
    private val SENTENCE_END = charArrayOf('.', '!', '?', '…', '。', '！', '？')

    /** Inserts dictated [raw] at the cursor of [current], capitalizing / punctuating so
     *  consecutive dictations read as sentences. Never alters the user's existing text. */
    fun insert(current: TextFieldValue, raw: String): TextFieldValue {
        val spoken = raw.trim()
        if (spoken.isEmpty()) return current
        val cursor = current.selection.end.coerceIn(0, current.text.length)
        val before = current.text.substring(0, cursor)
        val after = current.text.substring(cursor)

        val prev = before.trimEnd()
        val startsSentence = prev.isEmpty() || prev.last() in SENTENCE_END || prev.endsWith("\n")
        var chunk = if (startsSentence) spoken.replaceFirstChar { it.titlecase() } else spoken
        // Each dictation is usually one thought: close it with a period if the recognizer didn't,
        // but only when it lands at the end of the text (don't punctuate mid-sentence inserts).
        if (after.isBlank() && chunk.last() !in SENTENCE_END && chunk.last().isLetterOrDigit()) {
            chunk += "."
        }
        val sep = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
        val newText = before + sep + chunk + (if (after.isNotEmpty() && !after.first().isWhitespace()) " " else "") + after
        val newCursor = (before + sep + chunk).length
        return TextFieldValue(newText, TextRange(newCursor))
    }
}
```

Behaviour examples (add them as unit tests in `app/src/test/.../DictationTextTest.kt`):

| Existing text (cursor at `|`) | Dictated | Result |
|---|---|---|
| `|` (empty) | `we watched the sunset` | `We watched the sunset.|` |
| `We watched the sunset.|` | `then we ate pasteis de nata` | `We watched the sunset. Then we ate pasteis de nata.|` |
| `We watched the sunset|` (no period) | `from the miradouro` | `We watched the sunset from the miradouro.|` |
| `Great day. | It rained later.` | `the food was amazing` | `Great day. The food was amazing| It rained later.` |
| `Hello!|` | `What a view?` (already formatted, API 33+) | `Hello! What a view?|` |

**E4. Light normalization on save, title only.** Capitalize the first character of the title if it's
a lowercase letter (`title.trim().replaceFirstChar { it.titlecase() }`). **Don't** auto-change the
description on save, because users may write lowercase on purpose.

**Scope notes:**
- Don't add dictation to other fields in this release.
- Don't build a custom `SpeechRecognizer` UI. Keep the system dialog (it handles permissions and
  offline packs).
- CJK locales (ja, zh-CN): `titlecase()` is a no-op there and the CJK full stops are in
  `SENTENCE_END`, so this is safe. Make sure a unit test covers a Japanese string.

Files: `NewEditEntryScreen.kt`, `util/DictationText.kt` (new), `app/src/test/.../DictationTextTest.kt` (new)

---

## F. Localized release notes for this release

**Problem:** Play's "What's new" text is English on the German and Portuguese listings. Only
`app/src/main/play/release-notes/en-US/default.txt` exists.

**Fix:** with the `ship-to-play` skill, write this release's notes **in every store language**, one
file per Play locale. The Gradle Play Publisher picks them up automatically:

```
app/src/main/play/release-notes/
  en-US/default.txt   de-DE/default.txt   es-ES/default.txt   fr-FR/default.txt
  it-IT/default.txt   ja-JP/default.txt   nl-NL/default.txt   pl-PL/default.txt
  pt-PT/default.txt   pt-BR/default.txt   sv-SE/default.txt   zh-CN/default.txt
```

Each ≤ 500 characters. Lead with user-facing changes (smarter dictation that punctuates and
capitalizes, cursor-position dictation, clearer Premium labels, onboarding in your language). Don't
mention analytics. **pt-BR** must use Brazilian Portuguese ("app", "você", "assinatura"), not a copy
of pt-PT.

Files: `app/src/main/play/release-notes/*/default.txt`

---

## Summary

| # | Change | File(s) |
|---|--------|---------|
| A | Firebase funnel events + paywall `source` nav arg | `util/Analytics.kt` (new), `TravelJournalApp.kt`, `JournalViewModel.kt`, `Screen.kt`, `NavGraph.kt`, `PurchaseScreen.kt`, `SettingsScreen.kt`, `ProfileScreen.kt`, `NewEditEntryScreen.kt` |
| B | Honest freemium onboarding copy, all onboarding text moved to strings | `OnboardingScreen.kt`, `strings.xml` ×11 |
| C | One-time soft paywall after the first saved entry | `PreferencesManager.kt`, `JournalViewModel.kt`, `NavGraph.kt`, `PurchaseScreen.kt`, `strings.xml` ×11 |
| D | `PremiumBadge` on locked features (free users only) | `ui/components/PremiumBadge.kt` (new), `SettingsScreen.kt`, `JournalListScreen.kt`, `strings.xml` ×11 |
| E | Keyboard capitalization, recognizer formatting, cursor-aware sentence joining, title capitalized on save | `NewEditEntryScreen.kt`, `util/DictationText.kt` (new), `DictationTextTest.kt` (new) |
| F | Release notes in all 12 Play locales (incl. pt-BR) | `app/src/main/play/release-notes/*/default.txt` |

**Knock-on for Cowork after shipping:** the Help & About FAQ may need a line on dictation
punctuation, and the Play listing's ✍️ section could mention smarter dictation. No Privacy Policy
change is needed: analytics and app interactions are already listed there and in Data safety.
