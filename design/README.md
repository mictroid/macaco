# Design source assets

Editable source artwork for Macaco's branding. These SVGs are the source of truth; the files
shipped in the app (rasterized webp / adaptive-icon XML) are *generated* from them — edit the SVG
here, then regenerate, don't hand-edit the outputs.

| File | What it is | Generates |
|------|------------|-----------|
| `macaco_monkey_icon.svg` | App launcher icon (the monkey mark). | `app/src/main/res/mipmap-*/ic_launcher.webp` + `ic_launcher_round.webp`, and the adaptive-icon drawables (`drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`, wired via `mipmap-anydpi-v26/ic_launcher.xml`). |
| `macaco_splash.svg` | Cold-start splash artwork — visual reference, *not* a shipped image. | Nothing directly. `SplashScreen.kt` rebuilds the splash in Compose: it reuses `R.drawable.ic_launcher_foreground` (the monkey) over a radial teal gradient (`macacoBrandBackground()`) with the wordmark + tagline as text. The teal/gold `Color` constants and gradient stops are hand-sampled from this SVG (and shared with `AppLockScreen`). Update the colors here → update the constants at the top of `SplashScreen.kt`. |

## Regenerating the launcher icon

Use **Android Studio → New → Image Asset** (Asset Studio): pick *Launcher Icons (Adaptive and
Legacy)*, set the foreground to `macaco_monkey_icon.svg`, and let it write the `mipmap-*` densities
+ adaptive-icon XML. That keeps every density and the round/monochrome variants in sync.

## Why these live in git

The app only ships the *rasterized* outputs. Without the SVGs the artwork can't be re-edited at the
source, so they're version-controlled here rather than left only in the Drive backup
(`G:\My Drive\2026\Wanderlog`, where the historical debug APKs are also archived).
