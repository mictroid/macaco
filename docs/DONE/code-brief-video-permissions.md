# Macaco — Video: Fix Media Permissions (videos unreadable after reinstall) + Manifest Cleanup

Fixes QA V1/V8/V9 (`docs/qa-video-review-2026-07-04.md`): the app never requests
`READ_MEDIA_VIDEO`, so on Android 13+ the survive-uninstall mechanism silently fails for
videos; plus two manifest cleanups. Touches `MainActivity.kt`, `AndroidManifest.xml`.

---

## Change 1 — Request READ_MEDIA_VIDEO alongside READ_MEDIA_IMAGES (API 33+)

**Problem:** Entry videos persist to shared `Movies/Macaco` so cloud-synced entries can show
them after a reinstall — but after reinstall the app no longer owns those MediaStore rows, and
on API 33+ reading them requires `READ_MEDIA_VIDEO`. `MainActivity` requests only
`READ_MEDIA_IMAGES`, so every video falls back to a Drive re-download (or shows nothing
without Drive).

**Fix:** Switch the launch-time request to `RequestMultiplePermissions`, asking for both media
permissions on 33+ (one system dialog; the pre-33 cases stay single-permission — the legacy
storage permissions already cover video).

```kotlin
// BEFORE (MainActivity.setContent, ~line 90)
            val mediaPermission = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_MEDIA_IMAGES
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> Manifest.permission.READ_EXTERNAL_STORAGE
                else -> Manifest.permission.WRITE_EXTERNAL_STORAGE // <=28: grants the read+write storage group
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(context, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(mediaPermission)
                }
            }

// AFTER
            // API 33+ splits media access per type — request BOTH so reinstalled devices can
            // re-read entry photos (Pictures/Macaco) AND videos (Movies/Macaco). Below 33 the
            // legacy storage permissions cover both types.
            val mediaPermissions = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) // <=28: read+write group
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {}
            LaunchedEffect(Unit) {
                val missing = mediaPermissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }
```

**File:** `MainActivity.kt`

---

## Change 2 — Manifest cleanup: drop RECORD_AUDIO, fix invalid attributes

**Problem A:** `RECORD_AUDIO` is declared but the app never records audio itself — recording
goes through the system camera app via `ACTION_VIDEO_CAPTURE`, and the *camera app* holds its
own audio grant. An unused dangerous permission is Play-review friction and shows in the
listing's permission list for nothing.

**Problem B:** `android:minSdkVersion` is not a valid `<uses-permission>` attribute (it's
silently ignored) — present on both `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`. Harmless but
misleading; the permissions don't exist below 33 anyway, so just drop the attribute.

```xml
<!-- BEFORE -->
    <uses-permission
        android:name="android.permission.READ_MEDIA_IMAGES"
        android:minSdkVersion="33" />
    …
    <!-- In-app video recording keeps the audio track. -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <!-- Video gallery pick on API 33+. -->
    <uses-permission
        android:name="android.permission.READ_MEDIA_VIDEO"
        android:minSdkVersion="33" />

<!-- AFTER -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    …
    <!-- Re-read entry videos in Movies/Macaco after a reinstall on API 33+
         (recording itself happens in the system camera app, which holds its own
         RECORD_AUDIO grant — we don't need one). -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

Keep the `<queries>` block for `VIDEO_CAPTURE` — that one is correct and required.

**File:** `AndroidManifest.xml`

---

## Scope notes

- No `READ_MEDIA_VISUAL_USER_SELECTED` (API 34 partial access) handling in this pass — the
  current request already surfaces the system's "select photos and videos" flow; refining the
  partial-grant UX is a future decision.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Request `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` together on API 33+ | `MainActivity.kt` |
| 2 | Remove `RECORD_AUDIO`; drop invalid `minSdkVersion` attributes | `AndroidManifest.xml` |
