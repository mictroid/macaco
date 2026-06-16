# Worklog

Running log of notable work sessions. Newest first.

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

## 2026-06-15 — Galaxy S8+ ADB connection (IN PROGRESS, paused for PC reboot)

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
