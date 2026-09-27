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
- `ui/SettingsScreen.kt` — long-press the home screen to open it. Lets you
  pick a named style preset (Zen, Pixel, Samsung, OxygenOS, Mi) or tune each
  piece yourself: accent color, icon shape and size, font size, whether
  home-screen icons show labels, status bar icon color, and which widgets
  are on the home screen.
- `style/` — `LauncherStyle.kt` defines every customizable field
  (`CustomStyle`) and the five presets; `StylePreferences.kt` persists the
  current style and widget selection with Jetpack DataStore, so choices
  survive a restart.
- `widgets/Widgets.kt` — first-party home-screen widgets (Glance, Calendar,
  Note, Recent apps, Notifications) that can be added or removed from
  Settings. This hosts the launcher's *own* widgets, not third-party
  Android app widgets — real `AppWidgetHost` support (letting other apps'
  widgets be placed on the home screen) is a separate, larger piece of
  work. Two of these widgets are worth calling out:
  - **Recent apps** is *not* Android's system Overview/Recents screen — a
    regular installed launcher can't host that; it's our own most-recently-
    launched list, tracked by `StylePreferences.recordLaunch` every time
    `MainActivity`'s `onLaunch` fires, persisted with DataStore.
  - **Notifications** reads real system notifications via
    `notifications/ZenFoldNotificationListener.kt`, a
    `NotificationListenerService`. Android has no runtime-permission dialog
    for this — the user has to grant "notification access" by hand in
    system Settings, which the widget deep-links to
    (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`) until it's enabled.
    This is *not* the real pull-down notification shade, which (like
    Recents) belongs to SystemUI and isn't something a launcher can
    restyle or replace.
- `ui/theme/Theme.kt` — builds the Material3 color scheme from whatever
  `CustomStyle` is active, rather than a fixed palette.
- `ui/Wallpaper.kt` — a soft two-glow gradient (in the active style's own
  accent/secondary colors) drawn behind the home screen and Settings. It
  exists specifically to give the blur below something with real detail to
  blur — a flat color blurs into itself.
- Real backdrop blur via [Haze](https://github.com/chrisbanes/haze)
  (`dev.chrisbanes.haze:haze`, pinned to `0.7.3` in
  `app/build.gradle.kts` — check Maven Central / the project's releases
  page and bump this if that version's been pulled). `Wallpaper` is the
  blur source (`Modifier.haze(state)`); the home-screen widget cards and
  the preset rows in Settings are the blurred glass surfaces
  (`Modifier.hazeChild(state, shape)`). Haze degrades gracefully on
  pre-Android 13 devices (tint only, no blur) rather than crashing.
  **Not yet extended to** the app drawer's bottom sheet — Compose's
  `ModalBottomSheet` renders through its own overlay, and wiring Haze
  through that boundary wasn't verified here.

This is an early, working skeleton — not yet the full feature set discussed
(gesture navigation, real third-party widget hosting, hidden space, lock
screen). It also has not been built or run on a device/emulator from this
environment (no Android SDK here) — open it in Android Studio and let
Gradle sync before trusting any of this compiles.

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
