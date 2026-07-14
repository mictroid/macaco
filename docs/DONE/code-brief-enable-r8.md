# Macaco — Release Build: Turn On R8/ProGuard Shrinking + Obfuscation

Covers `app/build.gradle.kts` (release build type) and `app/proguard-rules.pro`. The release build
type currently ships with `isMinifyEnabled = false` — the AAB is unshrunk and unobfuscated. This
brief turns on R8 with the specific keep rules Macaco's dependencies need to survive shrinking,
since two of them (Google's Drive API client and kotlinx.serialization) are reflection-based and
will silently break at runtime — not at compile time — if their model classes get stripped or
renamed.

**This is a runtime-risk change, not a pure config flip.** R8 failures don't show up in
`assembleRelease` — they show up as a blank Drive sync, a crash on backup import, or a JSON parse
failure at 2am in production. The scope note at the bottom of this brief spells out exactly what
must be exercised on a real signed release build before this ships to any track.

---

## 1. Turn on minification + resource shrinking

**Problem:** `app/build.gradle.kts`, release build type, line 66:

```kotlin
// BEFORE — app/build.gradle.kts, release block
buildTypes {
    release {
        isMinifyEnabled = false
        if (keystorePropertiesFile.exists()) {
            signingConfig = signingConfigs.getByName("release")
        }
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        ndk {
            debugSymbolLevel = "FULL"
        }
    }
}
```

**Fix:** flip `isMinifyEnabled` to `true` and add `isShrinkResources = true` (shrinking resources
requires minify to be on, and only helps once it is — Play's unused-drawable/layout pruning).

```kotlin
// AFTER — app/build.gradle.kts, release block
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        if (keystorePropertiesFile.exists()) {
            signingConfig = signingConfigs.getByName("release")
        }
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        ndk {
            debugSymbolLevel = "FULL"
        }
    }
}
```

**Files:** `app/build.gradle.kts`

---

## 2. Keep rules — Google Drive API client (highest-risk dependency)

**Problem:** `google-api-services-drive` / `google-api-client` (used by `DrivePhotoSync.kt`) builds
its request/response models on **runtime reflection** over `com.google.api.client.json.GenericJson`
and `GenericData` — field names are read by reflection to build the JSON wire format. Without keep
rules, R8 renames or strips those fields and Drive upload/download starts failing silently (requests
either 400 or serialize as empty objects) with no compile-time warning.

**Fix:** add to `app/proguard-rules.pro`:

```proguard
# Google API Client (Drive) — reflection-based JSON model, must not be renamed/stripped.
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * extends com.google.api.client.json.GenericJson {
  <fields>;
}
-dontwarn com.google.api.client.**
```

**Files:** `app/proguard-rules.pro`

---

## 3. Keep rules — kotlinx.serialization (`TravelEntry` + backup JSON)

**Problem:** `TravelEntry` is `@Serializable` and is round-tripped through `kotlinx.serialization`
JSON in `JournalBackup.kt` (the local `.zip` export/import feature). Note this is *not* how entries
reach Firestore — `CloudEntrySync.kt` writes/reads via manual `toMap()` / field-by-field
`DocumentSnapshot` getters, so Firestore sync itself has no reflection risk. The serialization risk
is isolated to backup export/import. kotlinx.serialization's runtime library ships its own consumer
ProGuard rules (bundled since 1.6.x, and this project is on 1.7.3), which normally cover the
generated `$serializer` companion — but add an explicit keep as a defensive backstop, since a silent
break here means backups restore as corrupted/empty entries with no crash to signal it:

```proguard
# kotlinx.serialization — defensive backstop on top of the library's bundled consumer rules.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep,includedescriptorclasses class com.houseofmmminq.macaco.data.model.**$$serializer { *; }
-keepclassmembers class com.houseofmmminq.macaco.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.houseofmmminq.macaco.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

**Files:** `app/proguard-rules.pro`

---

## 4. Keep rules — crash symbolication

**Problem:** with R8 on, Crashlytics stack traces from real users come back obfuscated
(`a.b.c` instead of `PurchaseScreen.kt`) unless line-number info is preserved and the mapping file
is uploaded. The `firebase-crashlytics` Gradle plugin (already applied) auto-uploads `mapping.txt`
on release builds once minification is on — no extra Gradle wiring needed — but the source-file/
line-number attributes must be explicitly kept or R8 strips them by default.

**Fix:** add to `app/proguard-rules.pro`:

```proguard
# Keep line numbers for readable (and Crashlytics-deobfuscatable) release stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

**Files:** `app/proguard-rules.pro`

---

## 5. RevenueCat — verify, don't blanket-keep

**Note for Code, not a change to make blindly:** `com.revenuecat.purchases:purchases:8.10.0` ships
its own bundled consumer ProGuard rules (standard practice for Kotlin SDKs this recent), so no
manual keep rules should be needed for `Package`/`StoreProduct`/`Offerings`. **Do not** add a broad
`-keep class com.revenuecat.** { *; }` preemptively — it defeats shrinking for the largest single
dependency in the app for no proven reason. Instead, this is the #1 thing to watch for in the
on-device QA pass below: if the paywall fails to load offerings or a purchase/restore call throws
on the release build specifically (works on debug, fails on release-with-minify), that's the signal
the bundled rules aren't sufficient and an explicit keep is needed — add it then, scoped as
narrowly as the failure shows.

---

## Scope: what must be tested before this ships to any track

R8 bugs are runtime-only. `./gradlew bundleRelease` succeeding proves nothing about correctness —
it only proves the build compiled. Before this goes out on any Play track (including Closed
testing), build a signed release AAB/APK and manually exercise, on a real device:

1. **Google Sign-In + Firestore sync** — sign in, confirm entries load and a new entry saves and
   appears in Firestore.
2. **Google Drive backup** — connect Drive in Settings, save an entry with a photo, confirm it
   uploads (this is the highest-risk path per section 2).
3. **Local backup export + import** (Settings → premium backup) — export a `.zip`, then import it
   on a clean state, confirm entries and photos restore correctly (exercises section 3).
4. **RevenueCat paywall + purchase/restore** — open the paywall, confirm offerings/prices load,
   run a test purchase and a "Restore Purchases" (per section 5).
5. **Maps rendering** on the entry location picker/display, if a map screen is present.
6. **Crashlytics** — force a test crash, confirm it appears deobfuscated in the Crashlytics console
   within a few minutes of the `mapping.txt` upload (confirms section 4 worked end-to-end).

If any of these fail only in the release-minified build (not in debug), that narrows the cause to
a missing keep rule for whatever code path failed — add a targeted rule, don't disable minify
again as the fix.

---

## Summary

| # | Change | File |
|---|--------|------|
| 1 | `isMinifyEnabled = true`, `isShrinkResources = true` | `app/build.gradle.kts` |
| 2 | Keep rules for Google Drive API client (reflection-based JSON models) | `app/proguard-rules.pro` |
| 3 | Keep rules for kotlinx.serialization (`TravelEntry` + backup JSON) | `app/proguard-rules.pro` |
| 4 | Keep line-number attributes for Crashlytics deobfuscation | `app/proguard-rules.pro` |
| 5 | RevenueCat — no blanket keep; verify via on-device QA, add targeted rule only if a real failure surfaces | — |
| — | Manual on-device QA pass on a signed release build (6-point checklist above) — required before shipping to any track | — |
