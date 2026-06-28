# Code Brief (RETROACTIVE): Launcher icon — monkey too small on home screen

> **Status: ALREADY IMPLEMENTED (2026-06-28), not a request.** This brief is written *after the fact*
> to document a direct user-reported fix for Cowork's records — there was no upstream brief because it
> came from an on-device observation, not the Cowork pipeline. Code is done and builds clean; unpushed,
> batched with the backup-export fix. Included here for review/traceability.

## Problem
The Macaco launcher icon reads too small on the home screen — the monkey fills only ~57% of the icon,
vs ~70–100% for peer app icons, so it looks lost inside its background. (User noticed on the A53,
2026-06-27.)

Two compounding causes:
1. `drawable/ic_launcher_foreground.xml` wraps the art in a `<group>` scaled `scaleX/scaleY = 0.9`
   ("so the ears stay inside the adaptive-icon safe zone").
2. The artwork itself only spans ~64% of its 1024×1024 viewport, so after the 0.9 scale the monkey
   occupies ~57% of the 108dp foreground.

## Constraint that shaped the fix
The monkey's **ears are the widest element** and sit on the horizontal axis, so they are what limits
how large the art can go before a *circular* launcher mask (e.g. Pixel) clips them. The adaptive-icon
"safe zone" (guaranteed un-clipped on any mask) caps the ears at roughly scale 1.0.

Decision (user-chosen via AskUserQuestion): go **bolder at scale 1.15** (~75% fill) rather than the
conservative 1.0 (~66% fill). At 1.15 the ear *tips* slightly exceed the strict-circle safe zone — a
non-issue on the user's Samsung squircle mask and in the Play listing; the very tips could graze only
on a Pixel-style round mask. Accepted tradeoff for a clearly larger icon.

## Key design choice — do NOT scale the shared drawable
`ic_launcher_foreground.xml` is **reused in 15 in-app screens** (SplashScreen, LoginScreen,
OnboardingScreen, JournalList/Map headers, Profile, Settings, AppLock, Purchase, Subscription,
HelpAbout). There the 0.9 scale provides intentional padding/breathing room. Editing the shared
drawable would have enlarged the monkey in all of those and risked layout regressions. So the launcher
got its **own copy**; the shared drawable was left untouched.

## What was changed (4 files)
| File | Change |
|------|--------|
| `app/src/main/res/drawable/ic_launcher_foreground_icon.xml` | **New** — launcher-only copy of the foreground art, identical paths, group scale **1.15** (vs 0.9). Header comment flags it must stay in sync with the shared art. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | `<foreground>` repointed to `@drawable/ic_launcher_foreground_icon`. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Same `<foreground>` repoint. |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed-icon group scale bumped **0.9 → 1.15** in place (this drawable is the themed launcher layer only, not reused in-app, so edited directly to keep the themed icon consistent with the regular one). |

**Unchanged on purpose:** `ic_launcher_foreground.xml` (the shared in-app drawable) stays at 0.9.

## Scope & caveats
- Affects **adaptive icons (API 26+)** only. On Android 7.x (API 24–25) the launcher uses raster
  `mipmap-*dpi/ic_launcher` PNGs, which were **not** regenerated — negligible device share; not the
  A53. (Follow-up only if API 24–25 ever matters.)
- **Maintenance:** `ic_launcher_foreground_icon.xml` duplicates the art. If the monkey artwork ever
  changes, update both it and `ic_launcher_foreground.xml`.

## Verification
- `:app:processDebugResources` — green.
- full `assembleDebug` — green.
- On-device A53 visual confirmation: **owed** (verify the home-screen icon + that the themed icon and
  the 15 in-app uses of the *shared* drawable are unaffected) once shipped.
