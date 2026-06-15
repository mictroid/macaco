# Worklog

Running log of notable work sessions. Newest first.

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
- Finish the Play Console internal-testing rollout (testers list, license testing) so **real test
  purchases** work — currently "product not available" because the sideloaded debug build can't
  transact. The promo route already covers using the app on the A53.
- Once the build is installed from the Play track, verify the real purchase → entitlement flow
  end-to-end.
