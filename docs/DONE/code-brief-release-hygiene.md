# Macaco — Release Hygiene: Strip Debug Logs, Tailor Backup Rules, Fix Comment Drift

Small production-hardening batch before the Play production push. Closes findings L2, L5, and L6
from `docs/qa-report-2026-07-11.md`. Touches `MapScreen.kt`, `MainActivity.kt`, `JournalBackup.kt`,
and the two backup-rules XML stubs. No behavior change visible to users.

---

## Change 1 — Remove `Log.d("MapCamera", …)` diagnostics from release code

**Problem:** `MapScreen.kt` still emits three verbose `Log.d("MapCamera", …)` camera-fitting
diagnostics (v11/v12/v13 debugging, feature stable since vc63) in **release** builds — minify is
off, so nothing strips them. They leak layout internals to logcat and are pure noise now.

**Fix:** Delete the three `Log.d` statements. **Keep** the `Log.e("MapCamera", "v12: move() threw…")`
— that one is a real failure signal. If the `Log` import becomes unused afterwards, remove it too
(it won't — `Log.e` remains).

```kotlin
// BEFORE (1 of 3) — after "fitLngCenter = lngCenter", ~line 304
                Log.d("MapCamera", "v11: lngSpan=${"%.1f".format(lngSpan)}° density=$density " +
                    "lngZ=${"%.2f".format(lngZoom)} latZ=${"%.2f".format(latZoom)} " +
                    "→zoom=$zoom map=${mapSizePx.width}×${mapSizePx.height}px " +
                    "center=(${"%+.1f".format(latCenter)},${"%+.1f".format(lngCenter)})")

// BEFORE (2 of 3) — inside `if (moved) {`, after `val appliedZoom = …`, ~line 325
                Log.d("MapCamera", "v12: applied zoom=$appliedZoom (requested=$requestedZoom) " +
                    "clamp=${if (appliedZoom > requestedZoom + 0.2f) "YES — SDK floor active" else "no"}")

// BEFORE (3 of 3) — inside `if (abs(latCenterVisible - fitLatCenter) > 0.5) {`, ~line 348
                        Log.d("MapCamera", "v13: lat reframe ${"%.1f".format(fitLatCenter)}→" +
                            "${"%.1f".format(latCenterVisible)} (${visibleLatlngs.size} visible pins)")

// AFTER — all three lines deleted. Surrounding code unchanged, including:
//   Log.e("MapCamera", "v12: move() threw — camera not positioned", moveResult.exceptionOrNull())  ← KEEP
```

File: `app/src/main/java/com/houseofmmminq/macaco/ui/screens/MapScreen.kt`

---

## Change 2 — Tailor `data_extraction_rules.xml` / `backup_rules.xml`

**Problem:** the manifest declares `android:allowBackup="true"` with
`dataExtractionRules="@xml/data_extraction_rules"` and `fullBackupContent="@xml/backup_rules"`,
but both XML files are still the untouched IDE sample stubs (everything commented out → default
back-up-everything). The only local data is the DataStore file
`datastore/wanderlog_prefs.preferences_pb` (theme, reminders, onboarding, `app_lock_enabled`, and
the legacy local `is_purchased` fallback flag). Risk is low (no tokens), but a Google-cloud restore
onto a new device silently carries over `app_lock_enabled` and a possibly stale `is_purchased`.
Entries/photos/purchases all restore from Firestore/Drive/RevenueCat anyway — the prefs file has
no cloud-restore value.

**Fix:** exclude the DataStore file from **cloud backup** on both API paths. Keep
**device-to-device transfer** included (user-initiated, direct, and carrying the theme along is
nice). Note: backup rules are file-granular, so excluding a single key isn't possible — we exclude
the whole prefs file; after a cloud restore the app simply starts with default prefs.

```xml
<!-- AFTER — app/src/main/res/xml/data_extraction_rules.xml (API 31+), full file -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- Prefs (theme, reminders, app-lock flag, legacy local purchase flag) must not ride a
             cloud restore to a new device: app_lock_enabled would silently re-arm and a stale
             is_purchased could disagree with RevenueCat. Entries/photos restore via
             Firestore/Drive; purchases via RevenueCat. -->
        <exclude domain="file" path="datastore/wanderlog_prefs.preferences_pb" />
    </cloud-backup>
    <!-- device-transfer intentionally unrestricted: direct, user-initiated d2d migration may
         carry prefs (theme etc.) along. -->
</data-extraction-rules>
```

```xml
<!-- AFTER — app/src/main/res/xml/backup_rules.xml (API ≤ 30 auto-backup), full file -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- Same rationale as data_extraction_rules.xml (see there). -->
    <exclude domain="file" path="datastore/wanderlog_prefs.preferences_pb" />
</full-backup-content>
```

Replace the entire contents of both files (they currently contain only the commented sample
scaffolding). No manifest change needed — both files are already referenced.

Files: `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/xml/backup_rules.xml`

---

## Change 3 — Fix stale comments (one masked a real bug)

**Problem:** two comment drifts flagged in QA. The `JournalBackup` one actively misleads: it names
a function `downloadMissingVideos` that doesn't exist (video download lives inside the combined
`DrivePhotoSync.downloadMissing()`), which helped hide blocker B1.

**Fix (a):** `MainActivity.kt` ~line 87 — rebrand drift:

```kotlin
// BEFORE
            // Entry photos live in shared storage (Pictures/Wanderlog) so they survive uninstalls.

// AFTER
            // Entry photos live in shared storage (Pictures/Macaco) so they survive uninstalls.
```

**Fix (b):** `JournalBackup.kt` lines 98 and 320 — name the real function:

```kotlin
// BEFORE (line 98)
                    // small; their Drive IDs survive for downloadMissingVideos re-fetch.
// AFTER
                    // small; their Drive IDs survive for DrivePhotoSync.downloadMissing() re-fetch.

// BEFORE (line 320)
                // AND their videoFileIds so downloadMissingVideos re-fetches from Drive.
// AFTER
                // AND their videoFileIds so DrivePhotoSync.downloadMissing() re-fetches from Drive.
```

Files: `MainActivity.kt`, `data/sync/JournalBackup.kt`

---

## Out of scope (explicit)

- **R8/minify stays off** (QA L1): enabling it needs keep-rules for Drive/Gson/kotlinx-serialization
  plus a full regression pass — a deliberate standing decision, not part of this batch.
- **B1 (video-only Drive download)** is its own pending brief: `code-brief-drive-video-download-fix.md`.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Delete 3 `Log.d("MapCamera", …)` diagnostics (keep the `Log.e`) | `MapScreen.kt` |
| 2 | Exclude DataStore prefs from cloud backup (both API paths) | `xml/data_extraction_rules.xml`, `xml/backup_rules.xml` |
| 3 | Fix stale comments (Wanderlog path; nonexistent `downloadMissingVideos`) | `MainActivity.kt`, `JournalBackup.kt` |
