# Brief: Update Privacy Policy for Production

**Priority:** High (must be done before production submission)  
**File:** `privacy-policy.html` (in repo root, hosted at `mictroid.github.io/macaco/privacy-policy.html`)

## Changes required

This is a content-only update to the existing HTML file. Match the existing style and table format.

### 1. Update "no analytics" statement (~line 88)

**Current text (remove):**
> We do not collect location data automatically, track your usage behaviour, or use analytics SDKs.

**Replace with:**
> We do not collect location data automatically. We use Firebase Analytics (Google LLC) to collect
> aggregate, anonymised usage data — such as app opens, session length, and feature engagement —
> to help us improve the app. This data is not linked to your identity. You can opt out via your
> device's "Opt out of Ads Personalisation" setting.

### 2. Add Firebase Analytics row to the third-party services table

In the table listing third-party services (which already has Firebase Auth, Firestore, RevenueCat,
Google Drive), add:

| Third Party | Purpose | Privacy Policy |
|-------------|---------|----------------|
| Firebase Analytics (Google LLC) | Aggregate app usage insights | https://policies.google.com/privacy |
| Firebase Crashlytics (Google LLC) | Crash reporting and stability monitoring | https://firebase.google.com/support/privacy |

### 3. Update Section 6 — Account Deletion

**Current text:**
> To delete your account and associated data, contact us at houseofmmminq@gmail.com.

**Replace with:**
> You can delete your account directly in the app: go to **Profile → Delete Account**. This
> permanently removes all your journal entries from our servers and deletes your authentication
> record. Note: photos you have saved to your device gallery or Google Drive are stored in your own
> accounts and are not affected by in-app deletion.

### 4. Add Terms of Service reference

After the introductory paragraph or in a new "Legal" section, add:
> By using Macaco, you agree to our [Terms of Service](https://mictroid.github.io/macaco/terms-of-service.html).

### 5. Update the "Last updated" date

Change the last-updated date to today's date (or the date the changes are committed):
> Last updated: [current date]

## Notes

- No structural HTML changes needed — only text content updates.
- After committing `privacy-policy.html` to the repo's `gh-pages` or main branch (whichever serves
  GitHub Pages), the hosted URL updates automatically.
- `terms-of-service.html` is being created separately and also needs to be committed to GitHub Pages.
