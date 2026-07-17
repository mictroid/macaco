# Macaco — Repo housekeeping: working-tree cleanup, drop firebase-storage, fix CLAUDE.md drift

Three housekeeping items from `docs/qa-report-2026-07-17.md` (S1, D5, D8). No app behavior
changes except one dependency removal. Touches `app/build.gradle.kts`, `gradle/libs.versions.toml`,
`CLAUDE.md`, and the git working tree.

## Change 1 — resolve the uncommitted launcher-icon changes (S1) ⚠ needs Michael's call

**Problem:** the working tree has modified-but-uncommitted release assets:
`app/src/main/res/drawable/ic_launcher_monochrome.xml`,
`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`,
`app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`. vc72 was built by CI from pushed
master, so these edits did NOT ship; left dangling they'll either ride along with an unrelated
commit or be lost.

**Fix:** run `git diff` on the three files and show Michael a one-line summary of what the edit
does (likely an icon tweak). Then, per his answer: commit them as their own
`chore(icons): …` commit, or `git checkout --` them. **Do not decide unilaterally** — launcher
icons are user-visible brand assets.

Also sweep the untracked files: `docs/screenshots/**` and `docs/play-store-feature-images/**`
follow the existing convention of committed QA/listing assets — commit them under
`docs: add vc68–vc72 screenshots + listing images`. `Macaco-App-Names-Update-2026-07.docx` at the
repo root and `app-qa.skill` are working files, not repo content — ask Michael whether to move the
.docx into `docs/` and commit, or leave both untracked (if leaving, add them to `.gitignore` so
`git status` stays clean).

**Files:** git working tree, possibly `.gitignore`

## Change 2 — remove the unused firebase-storage dependency (D5)

**Problem:** `firebase-storage` is declared but nothing imports `FirebaseStorage` anywhere in
`app/src/main/java` (Drive is the media backend; Firebase Storage isn't even enabled on the
project — Spark plan, per `docs/security-review-2026-07-16.md`). Dead weight in the APK and one
more SDK surface for R8/App Check to consider.

**Fix:**

```kotlin
// BEFORE — app/build.gradle.kts, dependencies block
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)
```

```kotlin
// AFTER
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
```

```toml
# BEFORE — gradle/libs.versions.toml (line ~65)
firebase-storage = { group = "com.google.firebase", name = "firebase-storage-ktx" }
```

```toml
# AFTER — line deleted
```

**Files:** `app/build.gradle.kts`, `gradle/libs.versions.toml`

## Change 3 — fix the stale NavGraph diagram in CLAUDE.md (D8)

**Problem:** CLAUDE.md's architecture section still describes PurchaseScreen as an app-wide gate
and omits the Onboarding and VerifyEmail gates plus several screens. The live model (NavGraph.kt)
is: freemium (`FREE_ENTRY_LIMIT = 3`, premium enforced per-feature and at entry creation past the
limit), with bottom-tab navigation. Doc drift like this misleads every future brief.

**Fix:** replace the `NavGraph (Compose Navigation) — gate order, outermost first:` block in
CLAUDE.md with the live model:

```
NavGraph (Compose Navigation) — gate order, outermost first:
  blank box           ← while onboardingComplete == null (DataStore loading)
  → OnboardingScreen  ← first install (onboardingComplete == false)
  → SplashScreen      ← cold-start branded splash (once per process)
  → AppLockScreen     ← if app lock enabled, locked, and signed in (30s background re-lock,
                        cold-start lock in JournalViewModel.init)
  → blank box         ← while isPurchased == null (entitlement loading)
  → LoginScreen       ← currentUser == null (login required)
  → VerifyEmailScreen ← email/password account with unverified email (Google always passes)
  → NavHost (full journal, freemium — premium is enforced per-feature and at entry creation
             beyond FREE_ENTRY_LIMIT = 3, NOT as an app-wide wall):
      bottom tabs: JournalListScreen · MapScreen (Adventures) · ProfileScreen
      ├── NewEditEntryScreen (shared create + edit via Screen.NewEntry / Screen.EditEntry)
      ├── EntryDetailScreen  (swipe pager over the tag-filtered set; Drive-cached photos)
      ├── SearchScreen
      ├── SettingsScreen     (Drive sync, backup/restore, reminders, app lock, Print Book)
      ├── PrintExportScreen · YearInTravelScreen
      ├── PurchaseScreen     (Screen.Paywall — freemium/per-feature upsell)
      ├── SubscriptionInfoScreen · HelpAboutScreen · LoginScreen
```

While in there, spot-fix any other obviously stale line the diff touches (e.g. the top-level gate
description "PurchaseScreen ← isPurchased == false"), but keep this a surgical doc edit, not a
rewrite.

**File:** `CLAUDE.md`

## Verification

`assembleDebug` AND `assembleRelease` after Change 2 (confirms nothing needed firebase-storage,
including R8). `git status` clean afterwards. CLAUDE.md diagram matches `NavGraph.kt`'s actual
`when { }` order by inspection.

## Summary

| # | Change | File |
|---|--------|------|
| 1 | Commit-or-revert icon edits (ask Michael); commit/ignore untracked files | working tree, `.gitignore` |
| 2 | Drop unused `firebase-storage` dependency + toml alias | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| 3 | Replace stale NavGraph gate diagram | `CLAUDE.md` |
