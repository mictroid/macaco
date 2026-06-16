# Release AAB setup

What's needed to build and upload a signed release App Bundle to Google Play.

Current state: the `release` build type in `app/build.gradle.kts` has **no signing config**
(so `bundleRelease` would produce an *unsigned* AAB), and there is no release keystore — only
`~/.android/debug.keystore`. The steps below close that gap.

App facts: applicationId `com.houseofmmminq.macaco`, `versionCode = 3`, `versionName = "1.2"`,
minSdk 24, target/compile 36. Firebase/GCP project `macaco-499016`.

## The practical blockers

### 1. Create a release/upload keystore

Generate with the JDK's keytool (Android Studio's bundled JDK):

```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool" -genkeypair -v \
  -keystore "$HOME/macaco-upload.jks" -alias macaco-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Prompts for a keystore password, key password, and name/org. **Back up this file + passwords**
(e.g. to `G:\My Drive\Macaco-backup\`). Keep it **out of git**. With Play App Signing (step 4) a
lost upload key is recoverable, but don't rely on that.

### 2. Add a release signing config in Gradle

`release` currently references no key. Add a `signingConfigs` block reading from a git-ignored
`keystore.properties`, and point the release build type at it:

```kotlin
// app/build.gradle.kts — load near the top, beside the existing localProperties
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: "")
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            // ...existing proguardFiles...
        }
    }
}
```

`keystore.properties` (repo root, **git-ignored**):

```
storeFile=C:/Users/micke/macaco-upload.jks
storePassword=...
keyAlias=macaco-upload
keyPassword=...
```

Add `keystore.properties` and `*.jks` to `.gitignore`.

### 3. Build the AAB

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. **Bump `versionCode`** for every upload.

### 4. Enroll in Play App Signing

Recommended (default for new apps): Google holds the real app-signing key; you upload signed with
your *upload* key, so a lost upload key is recoverable.

### 5. Register the release SHA-1 in Firebase (easy to forget)

Google Sign-In only works for signing keys whose SHA-1 is registered in Firebase (project
`macaco-499016`). Currently only the **debug** SHA-1 is there. After enrolling in Play App Signing,
add **both**:
- the **upload-key** SHA-1, and
- the **Play App Signing** SHA-1 (Play Console → Setup → App signing shows it).

Without this, sign-in fails in the released build. (Get a key's SHA-1 with
`keytool -list -v -keystore <file> -alias <alias>`.)

## First-upload Play Console requirements

The first release (even internal testing) requires completing the app-content forms before the AAB
is accepted:
- Store listing (name, descriptions, icon, screenshots, feature graphic)
- Content rating questionnaire
- Data safety form
- **Privacy policy URL** — host `docs/privacy-policy.md` at a public URL first
- Target audience / ads declaration

## Already handled

- `MAPS_API_KEY` is injected from `local.properties` via a manifest placeholder — no action.
- `google-services.json` is present and committed.

## Automated upload (gradle-play-publisher)

The Triple-T **gradle-play-publisher** plugin (`com.github.triplet.play`, v4.0.0 — 3.x doesn't
support AGP 9) is applied in `app/build.gradle.kts`, configured to push the signed AAB to the
**internal** track:

```kotlin
play {
    serviceAccountCredentials.set(rootProject.file("play-service-account.json"))
    track.set("internal")
    defaultToAppBundles.set(true)
}
```

**One-time credential setup** (the only missing piece — the JSON is git-ignored and not in Drive yet):

1. **Google Cloud Console** (project `macaco-499016`) → enable the **Google Play Android Developer API**.
2. **APIs & Services → Credentials → Create credentials → Service account.** Name it e.g.
   `play-publisher`. No project roles needed.
3. On the service account → **Keys → Add key → JSON**. Download it and save as
   `play-service-account.json` at the **repo root** (git-ignored; also back it up to
   `G:\My Drive\Macaco-backup\`).
4. **Play Console → Users and permissions → Invite new user**, enter the service-account email
   (`…@macaco-499016.iam.gserviceaccount.com`). Grant app access to **Macaco** with at least
   *Release to testing tracks* (Releases) permission. (First invite can take a few minutes to
   propagate.)

**Then, to build + upload to internal testing in one shot:**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew publishReleaseBundle
```

This signs the release AAB (via `keystore.properties`) and uploads it to the internal track. Still
**bump `versionCode`** before each run. `track`/`releaseStatus` can be overridden per-run with
`--track` / `--release-status`. The very first upload of an app must still be done manually in the
Console (the API can't create the app), but Macaco is already past that.

## Related

- `docs/revenuecat-setup.md` — billing setup (also needs an uploaded build on a track).
- `CLAUDE.md` → **Backup** — debug.keystore / SHA-1 notes for Google Sign-In.
