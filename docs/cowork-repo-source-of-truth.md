# Cowork — Source-of-Truth Repo (read this before writing any brief)

**Purpose:** Make sure every code brief is written against the *live* Macaco repo, not the
stale clone. Briefs drafted against the old clone have the wrong package name, legacy
identifiers, and **guessed** method signatures — all of which have to be re-verified by hand
before they can be implemented. Following this doc removes that rework.

---

## ✅ The ONLY correct repo

| Thing | Correct value |
|---|---|
| Git remote | `git@github.com:mictroid/macaco` |
| Local path | `C:\Users\micke\android studio folder\wanderlog` |
| Kotlin package | `com.houseofmmminq.macaco` |
| Play `applicationId` | `com.houseofmmminq.macaco` |
| Firebase / GCP project | `macaco-499016` |
| App name (user-facing) | **Macaco** |

Source files live under `app/src/main/java/com/houseofmmminq/macaco/...`.

## ❌ Do NOT read from / do NOT mimic these

| Wrong thing | Why it's wrong |
|---|---|
| `C:\Users\micke\AndroidStudioProjects\MyApplication` | **Stale clone.** Different, abandoned project. Never use it. |
| package `com.example.myapplication` | Old template package — never appears in the live repo. |
| name "Wanderlog" | Pre-rebrand name. Legacy only. |
| Firebase project `wanderlog-11d28` | Migrated away from. Legacy only. |

If anything you're about to write contains `com.example.myapplication`, `MyApplication`,
`Wanderlog`, or `wanderlog-11d28`, **stop** — you're reading the wrong source.

---

## Rules for writing a brief

1. **Pull from the live repo only** — `git@github.com:mictroid/macaco`, branch `master`.
2. **Never guess a signature.** Quote the real function/class signature from the live source,
   and include the file path (e.g. `app/src/main/java/com/houseofmmminq/macaco/data/storage/CloudEntrySync.kt`).
   If you can't see the real signature, say so explicitly instead of inventing one.
3. **Use real identifiers** — package `com.houseofmmminq.macaco`, Firebase `macaco-499016`.
4. **Reference real architecture.** It's a single-module, single-activity, Compose-only MVVM +
   Repository app. Manual DI via `TravelJournalApp` as a service locator (no Hilt/Dagger).
   Entries sync per-user via Firestore (`CloudEntrySync`); photos back up to Google Drive
   (`DrivePhotoSync`); premium is gated by RevenueCat (`BillingManager`).
5. **Each brief must cite the files it touches** with their real paths, so they can be verified
   against the live tree before implementation.

## Quick self-check before sending a brief

- [ ] Every package reference is `com.houseofmmminq.macaco` (zero `com.example.*`).
- [ ] No "Wanderlog" / "wanderlog-11d28" / "MyApplication" anywhere.
- [ ] Every file path resolves under `app/src/main/java/com/houseofmmminq/macaco/`.
- [ ] Every signature is quoted from real source, not described from memory.
- [ ] Firebase project is `macaco-499016`.
