# ZenFold Launcher

A minimal Android home-screen launcher, built with Kotlin + Jetpack Compose.
Named "ZenFold" to keep the word "Zen" — Google Play doesn't block a shared
app title outright, but publishing under the exact name "Zen Launcher" would
risk a trademark complaint from existing apps using it (Cooee's ZEN
Launcher, ASUS ZenUI Launcher). The logo keeps the ensō (円相), the
hand-drawn zen circle, as its visual motif.

## What's here so far

- `MainActivity.kt` — registers as a real Android Home app (see the
  `AndroidManifest.xml` intent-filter) and hosts the Compose UI.
- `AppRepository.kt` — queries the real list of installed, launchable apps
  via `PackageManager`.
- `ui/HomeScreen.kt`, `ui/AppDrawer.kt`, `ui/AppIcon.kt` — clock + favorites
  grid, and a searchable app drawer as a bottom sheet.
- `ui/theme/` — a quiet, low-saturation Material3 theme (stone neutrals,
  one moss accent).
- `res/drawable/ic_zenfold_*.xml` — the ensō mark, used as the adaptive app
  icon and as an in-app logo.

This is an early, working skeleton — not yet the full feature set discussed
(gesture navigation, Shelf-style widgets page, hidden space, lock screen).

## Opening it

1. Install **Android Studio** (Koala or newer).
2. `File → Open` this folder — Gradle sync will fetch dependencies from
   Google's and Maven Central's repositories automatically.
3. Run on a device or emulator with **API 26+**.
4. To try it as your Home screen: after installing, press the device Home
   button — Android will prompt you to choose a launcher.

## Publishing to the Play Store (when ready)

- You'll need a Google Play Developer account (one-time $25 fee).
- Generate a signed release bundle: `Build → Generate Signed Bundle / APK`.
- Home-screen replacement apps are allowed, but Play review is stricter
  about permissions — this project currently only requests
  `EXPAND_STATUS_BAR`, which is low-risk.
- You'll need: a privacy policy URL, a feature graphic, phone screenshots,
  and a content rating questionnaire completed in Play Console.
- Double-check `applicationId` in `app/build.gradle.kts`
  (`com.zenfold.launcher`) is unique to you before your first upload — Play
  ties an app forever to whatever ID you upload first.
