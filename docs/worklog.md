# Worklog

Running log of notable work sessions. Newest first.

## 2026-06-16 — Macaco watermark on empty-state screens (`6683e25`)

Third Cowork brief: a subtle repeating line-art **macaco icon pattern** as an empty-state background.
New `ui/components/MacacoWatermark.kt` — a `drawMacacoIcon` `DrawScope` helper (goggled-monkey line
art: head, concentric ears, goggles, muzzle, nostrils) and a `MacacoWatermarkBackground` wrapper that
tiles jittered, randomly-sized icons in the theme's **`primary` colour at low alpha** (so it adapts
across all 7 themes), with a fixed RNG seed for a stable layout. Applied behind: the **journal-list
empty state**, the **entry-detail content when the description is blank**, and the **new/edit entry
form**. Brief was already theme-adaptive, so no colour deviation — only dropped a no-op `shuffle`.
Compiles clean; **not yet verified on-device** (chose to commit now rather than re-do the
uninstall/sideload dance on the A53). The new-entry-form placement is the densest screen and most
likely to want an alpha/spacing tweak or removal after a look.

### Built release AAB at vc7 / 1.4
Bumped `versionCode` **6→7** and `versionName` **1.3→1.4** (first versionName bump since 1.3 — vc7
adds the watermark on top of the fully-verified vc6). Clean `bundleRelease`; signed AAB at
`app/build/outputs/bundle/release/app-release.aab` (~22 MB). Only new feature vs vc6 is the
empty-state watermark — so on the next install, eyeball the empty states (especially the new-entry
form) and adjust alpha/spacing if needed.

## 2026-06-16 — Play purchase verified + in-app subscription management

### Play internal-testing purchase → entitlement verified ✅
Installed Macaco from the Play **internal-testing** track and ran a real test purchase. The full
chain works end-to-end: purchase completes in Play Billing → RevenueCat reports the `premium`
entitlement → the app unlocks (opens straight to the journal). This closes out the last open billing
item — the promo-entitlement workaround is no longer the only path; **real test purchases now work**.

### Added in-app subscription management (`bf86b23`)
Closed the launch gap where users had no in-app way to cancel/refund (also a Google recommendation):
- **`AppActions.manageSubscriptions()`** — deep-links to the Play subscription centre
  (`play.google.com/store/account/subscriptions?package=…`).
- **`SubscriptionInfoScreen`** — a "Manage subscription" `OutlinedButton`, shown only for recurring
  plans (`currentPlanId` contains `annual`/`monthly`); hidden for lifetime, which has nothing to cancel.
- **`HelpAboutScreen`** — new FAQ entry "How do I cancel or get a refund?" explaining the Play-billing flow.
- Strings (`subscription_manage`, `help_faq_q/a_billing`) added across all 11 locales.

**On-device test (A53):** the promo entitlement carries no real product id, so the button stays
hidden there. Forced `isRecurring = true` temporarily, sideloaded the debug build (had to uninstall
the Play build first — signature mismatch), and confirmed the button renders, the deep link opens the
Play subscriptions page, and the FAQ entry reads correctly. Reverted the temp override and reinstalled
the A53 from the Play track (premium restored via the account-tied entitlement).

### Fixed: Google sign-out didn't show the account picker (`1f97c91`)
After signing out from a Google login, the next "Sign in with Google" silently reused the previous
account — you could never switch Google accounts. Cause: `FirebaseAuthRepository.signOut()` only
cleared Firebase auth and left the legacy GMS `GoogleSignInClient`'s cached account intact. Fix: also
call `GoogleSignIn.getClient(...).signOut()` on sign-out (using the previously-unused `appContext`),
so the next `signInIntent` shows the account chooser. Shipped in vc6; **verified on-device
2026-06-16 — sign-out now shows the account picker.** ✅

### Added Adventures map theme setting + gold country names (`49921ed`)
Made the Adventures map (the `MapScreen` reached from the drawer) customizable:
- **Dark / Light / Standard** map theme, selectable in **Settings → Map** via a `FilterChip` row.
  Persisted as the `map_theme` DataStore pref (default Dark), exposed as `MapTheme` on the ViewModel,
  and applied live by `MapScreen` (mirrors the existing `AppTheme` plumbing).
- New `map_style_light.json` (light teal-tinted) for the Light option; Standard uses Google's default
  map (no style override).
- **Country labels are now Macaco bright gold (`#F0C840`)** in both branded styles — with a dark
  stroke on the light style for legibility.
- `MapTheme` enum added; `settings_map*` / `map_theme_*` strings across all 11 locales.

Shipped in vc6; **verified on-device 2026-06-16** — the Dark/Light/Standard selector works. ✅

### Built release AAB for Play (`a9c57a9`)
Bumped `versionCode` 4→5 and `versionName` 1.2→1.3, then built the signed release bundle
(`./gradlew bundleRelease`, signed with the upload keystore via the git-ignored `keystore.properties`).
Output: `app/build/outputs/bundle/release/app-release.aab` (~22 MB). Bundles subscription management,
the sign-out fix, and the map theme feature. Same two non-blocking warnings expected on upload (no
mapping file — `isMinifyEnabled = false` — and native debug symbols). **vc5 was uploaded to the Play
internal-testing track** (superseded by vc6 below — see the next entry's correction).

### Set up gradle-play-publisher (`9f4fbcc`)
Wired the Triple-T `com.github.triplet.play` plugin so `./gradlew publishReleaseBundle` can push the
signed AAB straight to the internal track. Hit a real AGP-compat issue — 3.12.1 fails on AGP 9.2.1
(`BaseAppModuleExtension` gone); **4.0.0** applies cleanly. Configured `track = internal`, AAB mode.
Credential (`play-service-account.json`, git-ignored) **not yet created** — that one-time
service-account setup (documented in `docs/release-setup.md`) is the only thing blocking automated
uploads.

### Branded the entry detail screen, theme-adaptive (`97b25cd`)
Followed an old design brief (found in the stale `MyApplication` clone — legacy `com.example.myapplication`
package) to bring `EntryDetailScreen` in line with the app's branding, but using **Material theme
colours instead of the brief's hardcoded teal/amber** so it holds up across all 7 selectable themes:
- Header bar → theme `primary` with `onPrimary` icons.
- Mood + date chips → filled `secondaryContainer` accent chips (same token as the journal-list date
  pill); tag chips → filled `secondaryContainer` matching list tags; location chip tinted `primary`.
- Blank descriptions now show a tappable **"Add your story…"** prompt (opens the editor) + a subtle
  bottom fade instead of dead space. String added across all 11 locales.
- Photo full-bleed (also in the brief) was already in place.
- Chose `secondaryContainer` over hardcoded brand colours partly because that old clone already tried
  "gold tag text across all screens" and **reverted** it. Shipped in vc6.
- **Verified on-device 2026-06-16** (driven over ADB with screenshots): header band, mood/date/location/
  tag chips, full-bleed photo, and the empty-description **"Add your story…"** prompt + bottom fade
  (tap → opens the editor). **Theme adaptivity confirmed** by switching to the Rose theme — the header
  and chips adopt the theme's colours (the deliberate deviation from the brief's hardcoded teal). ✅

### Fixed: Manage-subscription button never showed for subscribers (`9fbee37`)
Tester reported the "Manage subscription" button missing on a fresh account after buying **monthly**.
Root cause: the button (and the plan label) keyed off the entitlement's `productIdentifier`
containing `"monthly"`/`"annual"`, but **both base plans live under one product id (`macaco_premium`)**,
so it returned `"macaco_premium"` for every subscriber — the check never matched, the button never
showed for *anyone*, and subscribers were mislabeled "Lifetime". Fix: `BillingManager` now derives
**`manageableSubscription`** from `store == PLAY_STORE && expirationDate != null` (a real
auto-renewing Play sub), via a single `applyEntitlement()` helper shared by all RevenueCat callbacks;
`SubscriptionInfoScreen` gates the button on that and shows a neutral "Auto-renewing subscription"
label instead of "Lifetime". New string in 11 locales.

### Show which plan the user is on (`2282d0d`)
Display **Monthly / Annual / Lifetime** on the subscription page. Resolves cadence by matching the
entitlement's base-plan id (`productPlanIdentifier`) against the offering's monthly/annual packages
(`googleProduct.basePlanId`) — exact, since the product id alone can't distinguish them. Falls back to
the neutral label only when unresolved.

Both billing fixes shipped in vc6. **Verified on-device 2026-06-16:** with a real **monthly** Play
subscription, the "Manage subscription" button now shows on the Subscription screen. ✅ (Annual /
lifetime not separately re-checked, but share the same gating path.)

### Added contextual empty-state hints to the new/edit entry screen (`78c23c8`)
Second Cowork brief from the stale `MyApplication` clone — three "whispered guidance" hints in
`NewEditEntryScreen`, each shown only while its field is empty and gone on first interaction:
- Photos: "Add photos to bring your memory to life" (📷, below the photo row).
- Tags: "Try #beach, #food, #family" (below the tags field).
- Story: "Tap the mic to speak your memory" (🎤, below the description field).

Shared `HintRow` helper, **theme-adaptive (`primary` @ 60% alpha)** instead of the brief's hardcoded
teal. Story copy points at the screen's own mic button rather than the keyboard mic. Strings in all
11 locales. Shipped in vc6; **verified on-device 2026-06-16 — all three hints show.** ✅

### Rebuilt the 1.3 AAB at vc6 with all the latest changes
**Correction:** vc5 *was* uploaded to the Play internal-testing track (the earlier "not yet uploaded"
note was wrong — fixed above). Since vc5 was consumed, bumped `versionCode` **5→6** (still
`versionName` 1.3) and did a clean `bundleRelease`. The signed vc6 AAB at
`app/build/outputs/bundle/release/app-release.aab` (~22 MB) bundles everything since vc5: the
sign-out fix, gradle-play-publisher setup, entry-detail branding, the subscription button fix + plan
display, and the new-entry hints. **Uploaded to the Play internal-testing track.**

### vc6 on-device verification — COMPLETE ✅
Installed from the Play internal-testing track and verified every feature in the release on a real
Play build (some driven over ADB with screenshots, the entry-detail pass included a throwaway entry
since cleaned up):

| Feature | Result |
|---------|--------|
| Manage-subscription button (monthly sub) | ✅ shows; plan label correct |
| Google sign-out → account picker | ✅ picker appears |
| Entry-detail branding (header/chips/empty-state prompt) | ✅ + tap-to-edit |
| Entry-detail **theme adaptivity** (tested under Rose) | ✅ header/chips adopt theme |
| Map themes (Dark/Light/Standard selector) | ✅ |
| New-entry contextual hints (all three) | ✅ also theme-adaptive |

Nothing outstanding for vc6. Only open thread in the repo is the paused Galaxy S8+ ADB setup below.

## 2026-06-15 — Galaxy S8+ ADB connection (IN PROGRESS, paused for PC reboot)

> **2026-06-16 update — RNDIS issue RESOLVED; new blocker is USB debugging.** The phone now
> enumerates as **PID_6860 (MTP / File Transfer)** — the RNDIS device (PID_6863) is no longer
> present, so the Default USB Configuration change worked. But the composite device advertises only
> **MTP + Modem, no ADB interface**, and there are no undriven/problem devices — the signature of
> **USB debugging being off** on the phone. **Next step (on the phone, not Windows):** Developer
> options → toggle **USB debugging** off→on (and *Revoke USB debugging authorizations*), replug,
> accept the **"Allow USB debugging?"** prompt with *Always allow*. Then re-scan + `adb devices`.
> The Device-Manager / RNDIS driver steps below are now obsolete. (Paused again at user's request.)

**Goal:** add a Samsung Galaxy S8+ as a second USB-connected test device (alongside the A53, which
is on wireless ADB). Resuming after a planned PC reboot.

**Status: not connected yet.** `adb devices` only shows the A53
(`adb-RZCT80LVGNY-...` transport_id varies). The S8+ never enumerates as an ADB device.

**Root cause identified:** Windows keeps binding the S8+'s USB interface as a
**`SAMSUNG Mobile USB Remote NDIS Network Device`** (RNDIS / USB-Ethernet) instead of an
**Android ADB Interface**. Every Device-Manager fix bounced back to RNDIS because the *phone* is
actively advertising RNDIS as its USB mode — so it must be changed on the phone, not in Windows.

**Tried (all still left it as RNDIS):** enabled USB debugging; installed Samsung USB Driver;
confirmed USB tethering OFF; restarted adb server; uninstalled the RNDIS device.

**Driver facts (confirmed installed):**
- Samsung ADB inf is present at `C:\Program Files\Samsung\USB Drivers\25_escape\ssudadb.inf`
  (defines "SAMSUNG Android ADB Interface"); also in the Windows DriverStore.
- Google USB driver is NOT installed.

**Next steps after reboot:**
1. On the S8+: **Settings → Developer options → Default USB Configuration → set to File Transfer /
   MTP** (it was likely RNDIS/USB-tethering — the suspected real cause). Confirm USB debugging ON.
2. Replug; on the notification shade pick **File Transfer**. The RNDIS "Network Device" should be
   replaced by an MTP device + an ADB interface, and the "Allow USB debugging?" prompt should appear
   — accept with *Always allow*.
3. If it still binds as RNDIS: Device Manager → Network adapters → the RNDIS device → **Update
   driver → Have Disk →** `C:\Program Files\Samsung\USB Drivers\25_escape\ssudadb.inf` → pick
   **SAMSUNG Android ADB Interface**.
4. Then `adb devices` should show the S8+; install Macaco on it. With two devices attached, target
   each explicitly via `-s <transport_id>`.

**Note:** S8+ runs Android 9 (API 28), above the app's `minSdk 24`. Pre-Android-11 wireless ADB
still needs one working USB session first (`adb tcpip 5555` then `adb connect <ip>:5555`), so USB
must bind correctly at least once.

adb path: `C:/Users/micke/AppData/Local/Android/Sdk/platform-tools/adb.exe`. Note: `adb kill-server`
drops the A53's wireless link too — reconnect it afterward if needed.

## 2026-06-15 — RevenueCat live + first internal-testing build

**Focus: Wired up live RevenueCat billing and got the first internal-testing build ready for Play.**

### Billing went live
- Replaced the placeholder RevenueCat key with the real public SDK key (`goog_yRpbApeYi…`). This
  flips `BillingManager` from the local-fallback paywall to **live entitlement checks**. Committed
  (`a3d1dde`).
- Reverted an in-progress debug paywall-bypass experiment — superseded by the real key, so the
  commit is just the key change.

### Verified the full billing chain on the A53 test device
Installed debug builds and read RevenueCat logcat to confirm, end-to-end:
- Identity correctly tied to the Firebase uid (`6x4Gh8bHKUb6yNncd7KpgdcXok92`).
- Offering loads with both products: **`macaco_premium`** (€18.99/yr + 7-day trial · €3.09/mo) and
  **`macaco_lifetime`** (€40.99 one-time).
- `premium` entitlement attached to both products (confirmed dashboard-side).

### Unlocked the test device via promotional entitlement
- The old local-DataStore "premium flag" trick no longer works now that RevenueCat is live (the
  configured path ignores it).
- Granted a **promotional `premium` entitlement** to the test uid in RevenueCat. First attempt
  landed on the wrong customer (device kept getting HTTP 304, stayed paywalled); after re-granting
  on the exact uid, a relaunch pulled HTTP 200 with the entitlement and **the app opened straight to
  the journal**. ✅

### Prepared the internal testing track
- Built and signed the release **AAB** with the upload keystore (`versionCode 4`, `versionName 1.2`).
- Bumped `versionCode` 3→4 after Play rejected the duplicate. Committed (`7bcae83`), both commits
  pushed to `origin/master`.
- Reviewed the two Play upload warnings (missing mapping file + native debug symbols) — both
  non-blocking, safe to ignore for testing.

### Still open
- ~~Finish the Play Console internal-testing rollout (testers list, license testing) so **real test
  purchases** work.~~ **Done 2026-06-16** — see top entry.
- ~~Once the build is installed from the Play track, verify the real purchase → entitlement flow
  end-to-end.~~ **Done 2026-06-16** — verified working.
