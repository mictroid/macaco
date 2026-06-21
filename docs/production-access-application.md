# Macaco — Google Play Production Access Application

Use this when clicking "Apply for production" on the Play Console Dashboard.
Paste each answer into the corresponding field. Edit numbers/dates to match reality at the time.

---

## Part 1: About your closed test

**Q: How easy did you find it to recruit testers?**
→ Select: *Very easy* (or the most positive option available)

---

**Q: Provide information about the engagement you received from testers during your closed test.**

> We recruited 12 testers through the r/AndroidAppTesters subreddit and reached our goal within 24 hours of posting. Testers were asked to use the app as a real travel journal — creating entries, adding photos, setting moods and locations, and exploring the map view of their trips.
>
> Tester usage was broadly consistent with how we expect production users to interact with the app. Testers created journal entries, tested photo attachment, tried the Google Drive backup, and used the app across multiple sessions over the 14-day period. Several testers reported using the app on lower-end and older Android devices, which helped us identify and fix a device-specific biometric authentication issue on API 28 (Android 9).
>
> We would expect production users to behave similarly, with the addition of longer-term usage patterns such as accumulating many entries over months of travel.

---

**Q: Summarize the feedback that you received from testers, and let us know how you collected this feedback.**

> Feedback was collected via the in-app "Report an issue" email feature (which sends a pre-filled bug report to our support address), through the Play Console Testing Feedback page, and through direct replies to our Reddit post.
>
> Key issues identified and resolved during the closed test period:
>
> - **Biometric authentication failure on Samsung Galaxy S8 (Android 9 / API 28):** The app used a combined BIOMETRIC_STRONG | DEVICE_CREDENTIAL authenticator flag that is only valid on API 30+. Fixed by branching on SDK version and falling back to BIOMETRIC_STRONG with a cancel button on older devices.
> - **Blank screen after deleting an entry:** A race condition in the navigation stack caused a double popBackStack(), which removed both the entry detail screen and the journal list. Fixed by reordering the navigation and delete calls.
> - **Map screen defaulting to the ocean:** The map camera initialised at a default coordinate in the Gulf of Guinea before geocoding completed. Fixed by showing a loading indicator until the first location is geocoded.
> - **Email feedback templates not populating in Gmail:** Gmail ignores Intent EXTRA_TEXT/EXTRA_SUBJECT on ACTION_SENDTO intents. Fixed by encoding the subject and body directly in the mailto: URI.
> - **Onboarding skip button unresponsive:** The ViewPager was intercepting touch events above the Skip button. Fixed by adjusting z-order.
>
> All reported issues were resolved and shipped in updated builds during the testing window (versions 21–23).

---

## Part 2: About your app

**Q: Who is the intended audience of your app?**

> Macaco is designed for adult travelers and adventure seekers who want a private, cloud-synced digital travel journal. Our primary audience is people aged 20–45 who travel regularly — whether for leisure, backpacking, weekend trips, or long-term adventures — and want a dedicated place to capture memories, moods, photos, and locations from their journeys. Secondary users include people who travel occasionally but want to preserve memories in a more personal and structured way than social media allows.

---

**Q: Describe how your app provides value to users.**

> Macaco lets users build a private, searchable travel journal that syncs automatically across devices via Firebase. Every entry captures the full context of a memory: title, location, date, mood, photos, free-text notes, and custom tags. Key value propositions:
>
> - **Cloud sync:** Entries are instantly available on any device the user signs into, with no manual backup needed.
> - **Google Drive photo backup:** Entry photos are backed up to the user's own Google Drive, so memories are never lost even if the device is lost or replaced.
> - **Map view:** All entries with locations are geocoded and displayed on an interactive map, giving users a visual record of everywhere they've been.
> - **Privacy-first:** All data is stored in the user's own Firebase account and Google Drive — we do not have access to their content.
> - **On This Day:** The app surfaces memories from the same date in previous years, encouraging users to revisit and reflect.
> - **Reminders:** Optional periodic prompts encourage users to log memories while they're fresh.

---

**Q: How many installs do you expect your app to have in its first year?**
→ Select the range that covers **100–500** (or the closest option). As a new personal developer account launching a niche journaling app, this is a realistic conservative estimate for year one, with growth as word-of-mouth and Play Store visibility increase.

---

## Part 3: Production readiness

**Q: What changes did you make to your app based on what you learned during your closed test?**

> We shipped three build updates (vc21, vc22, vc23) during the closed testing period, directly addressing tester feedback:
>
> - Fixed biometric authentication on Android 9 devices (Samsung Galaxy S8 and similar)
> - Fixed blank screen after deleting a journal entry (navigation race condition)
> - Fixed map screen showing a default ocean location before geocoding completed
> - Fixed email feedback templates not populating in Gmail
> - Fixed onboarding skip button being unresponsive due to touch event interception
> - Fixed app lock not triggering correctly after OEM background-kill and cold restart
> - Improved readability of hint text on the entry creation screen
> - Improved the custom mood emoji picker (keyboard now opens automatically)
> - Improved the share sheet with a branded image card and emoji copy

---

**Q: Describe how you decided that your app was ready for production.**

> We determined Macaco was ready for production after confirming the following across the 14-day closed test:
>
> 1. **No crashes reported** across the testing period after the final build update (vc23).
> 2. **All tester-reported bugs resolved** and verified in subsequent builds shipped to the closed track.
> 3. **Core user flows functional end-to-end:** account creation, entry creation with photos, cloud sync across devices, Google Drive photo backup, map view, in-app purchase, and purchase restore.
> 4. **Cross-device stability:** tested on a range of Android versions from API 28 (Android 9) through API 35, including Samsung, Google Pixel, and other OEM devices.
> 5. **Store listing complete:** screenshots, description, privacy policy, and content rating all in place.
> 6. **Billing configured and tested:** RevenueCat subscription products (monthly, annual, lifetime) are live and tested via promotional entitlements.
