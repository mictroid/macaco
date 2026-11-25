# Macaco — Play Store: Capture Raw Screenshots via adb

Capture 8 clean, full-resolution phone screenshots of the live app over adb, plus collect the icon
artwork, into `docs/play-store-feature-images/raw/`. These are the raw inputs Cowork will composite
into the new Play Store screenshot set (English + German first). **No app code changes in this
brief.**

---

## Context (why)

A listing review on 2026-11-22 found the current 6 Play screenshots miss Macaco's main selling
points (privacy / App Lock, video entries, dictation, Year in Travel, On This Day), duplicate the
entry-detail screen, include an empty New Entry form, and have real notification icons in the
status bar. The store listing converts only ~3% of visitors. The new set needs fresh, clean raw
captures. Michael navigates the app on the phone; you handle adb, demo mode, capturing, naming and
checking.

---

## 1. Preflight

```bash
adb devices                      # exactly one device must be listed as "device" (not "unauthorized")
adb shell wm size                # record the physical resolution, e.g. 1080x2400
adb shell pm list packages | grep com.houseofmmminq.macaco   # app installed
mkdir -p docs/play-store-feature-images/raw
```

- If no device / "unauthorized": stop and ask Michael to enable USB debugging and accept the
  RSA prompt on the phone.
- If more than one device: ask which serial to use and pass `-s <serial>` on every adb call below.
- Ask Michael to set the phone to **light theme or the app's default teal theme** (whichever he
  wants in the store) and to keep the same theme for every shot.

---

## 2. Enter SystemUI demo mode (clean status bar)

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

After the first capture, view the image and confirm the status bar shows 12:00, full battery and
no notification icons. Some Samsung builds ignore demo mode. If it doesn't take, tell Michael and
continue anyway: Cowork will mask the status bar when compositing. Don't spend more than one
retry on it.

---

## 3. (Optional) Put a trip video on the phone

Michael has no entry with a video yet. Ask him for the path of a short trip clip on his PC
(ideally 5–15 s, landscape or portrait, no faces of strangers). Then:

```bash
adb push "<path-on-PC>" /sdcard/DCIM/Camera/macaco-demo-clip.mp4
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/DCIM/Camera/macaco-demo-clip.mp4
```

Michael then creates (or edits) an entry in the app and attaches the clip through the normal
picker. Don't write to Firestore or the app's storage directly.

---

## 4. Capture loop (interactive)

Work one shot at a time. For each row below: tell Michael what to set up, wait for him to say
**"shot <name>"** (or "ready"), then capture:

```bash
adb exec-out screencap -p > docs/play-store-feature-images/raw/<name>.png
```

Use `exec-out` (not `shell screencap` + `pull`), because it avoids CRLF corruption on Windows. If
you run under PowerShell, the `>` redirect re-encodes binary output and corrupts the PNG. Run this
step from Git Bash, or capture to the device and pull it instead:
`adb shell screencap -p /sdcard/_shot.png && adb pull /sdcard/_shot.png docs/play-store-feature-images/raw/<name>.png && adb shell rm /sdcard/_shot.png`.

| # | File name | Screen | Setup tips for Michael |
|---|-----------|--------|------------------------|
| 1 | `journal.png` | Journal list (Journal tab) | Scrolled to the top, several trips with photos visible, no tag filter active, no snackbar or toast showing. |
| 2 | `video-entry.png` | Entry detail of the entry with the video | Video thumbnail/player visible in the media area, title + location chips visible. A second take with the video playing is welcome (`video-entry-playing.png`). |
| 3 | `app-lock.png` | Macaco's own lock screen (🔒 + Unlock button) | Turn App Lock on in Settings, background the app for 30 s+, reopen. When the fingerprint/face prompt appears, tap **Cancel**. The system prompt is a secure window and cannot be captured, but Macaco's lock screen behind it can. |
| 4 | `dictation.png` | New/Edit Entry with dictation in progress or just finished | Ideally mid-dictation with the voice overlay visible. If the overlay comes out black (secure on some devices), capture right after with the dictated sentence filled in the description instead. |
| 5 | `year-in-travel.png` | Year in Travel screen (Profile → Year in Travel) | The recap at its most visual state (stats + map/photos visible). |
| 6 | `on-this-day.png` | Journal list showing the On This Day card | The card only appears when an entry is dated today's day and month in a **previous year**. If none exists, ask Michael to temporarily set an existing entry's date to 25 Nov of an earlier year, capture, then change it back. |
| 7 | `reel.png` | Adventure Reel preview/playback | A frame with a strong photo and the location/date overlay visible. Pause if possible. |
| 8 | `map.png` | Adventures (map) tab | Zoomed so several pins are visible (ideally 2+ continents). |
| 9 | `print-book.png` *(optional)* | Print Book / PrintExportScreen | Only if Michael wants it, for a possible 8th slot. |

After **each** capture, verify before moving on:

```bash
python3 - <<'PY'
from PIL import Image, ImageStat
import sys
p = "docs/play-store-feature-images/raw/<name>.png"
im = Image.open(p).convert("RGB")
print(im.size, "mean brightness:", round(sum(ImageStat.Stat(im).mean)/3, 1))
PY
```

- Size must match `wm size`. If it doesn't, the PNG is corrupted, so re-capture with the pull method.
- Mean brightness < 5 means a **black frame** (secure window). Tell Michael and use the fallback in
  the tips column.
- Also open the image to eyeball it: no keyboard covering content (unless it's intentional for
  dictation), no snackbars, no debug banners, no other personal data (email address on screen,
  real people's full names).

---

## 5. Icon artwork

Look for the original icon source (vector or a PNG ≥ 512×512 **without baked-in rounded corners
or grey background**). The Play icon currently uploaded has grey corners baked in, and Play rounds
it again on top.

1. Check the repo first: `assets/`, `app/src/main/res/` (`ic_launcher_foreground` drawable or
   vector, `mipmap-anydpi-v26/ic_launcher.xml` layers), `docs/`.
2. If an adaptive-icon foreground + background exists, export a flattened **512×512 full-bleed
   square** (background colour/drawable filling the whole square, foreground centred, no corner
   rounding, no shadow) as `docs/play-store-feature-images/raw/icon-512.png`.
3. If you can't produce a clean full-bleed version, don't fake one. Report what you found (paths +
   sizes) and ask Michael for the original artwork file.

---

## 6. Finish

```bash
adb shell am broadcast -a com.android.systemui.demo -e command exit
adb shell settings put global sysui_demo_allowed 0
```

- Remind Michael to revert any temporary entry date change (shot 6) and to turn App Lock back to
  his preferred setting.
- Delete the pushed demo clip only if Michael says so (`adb shell rm /sdcard/DCIM/Camera/macaco-demo-clip.mp4`).
- **Don't commit the raw PNGs to git** unless Michael asks. They're working inputs and they're large.
- Report: a list of files saved with resolution, which shots used a fallback, whether demo mode
  worked, and the icon status.

---

## Summary

| # | Task | Output |
|---|------|--------|
| 1 | Preflight: device, resolution, folder | `docs/play-store-feature-images/raw/` |
| 2 | Demo mode on (clean status bar) | — |
| 3 | Optional: push trip clip for the video entry | `/sdcard/DCIM/Camera/macaco-demo-clip.mp4` |
| 4 | Capture + verify 8 shots (+ optional print-book) | `raw/*.png` |
| 5 | Find or export the full-bleed 512×512 icon | `raw/icon-512.png` |
| 6 | Demo mode off, cleanup, report | — |
