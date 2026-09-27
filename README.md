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
- `ui/HomeScreen.kt` — live clock, widgets, a real 4×4 drag-to-arrange icon
  grid, and a glass search bar + dock. The dock holds the phone's own default
  dialer, SMS app, camera and browser; the grid starts from other defaults
  (gallery, maps, music, calendar...) plus recent apps
  (`AppRepository.homeApps`), but every icon can be dragged to any cell —
  positions are saved per app (`StylePreferences.homeLayout`), not a fixed
  list order. Long-press an icon without moving it for a menu: **App info**,
  **Remove from Home** (unlists it, doesn't touch the install), **Uninstall**
  (the real system uninstall flow). Swipe up or tap Search to open the
  drawer; swipe left for the Today panel (see `ui/TodayPanel.kt`); long-press
  *empty* space opens a quick widget on/off picker
  (`widgets/WidgetPickerOverlay`) — full Settings now lives in its own app,
  see below.
- `ui/TodayPanel.kt` — the page to the left of home (`HorizontalPager`,
  page 0). Real, first-party sections only: **Tasks** (an add/check-off/
  remove list, persisted by `tasks/TaskPreferences.kt`), **Calendar**
  (upcoming events via `CalendarContract.Instances`, gated behind a real
  `READ_CALENDAR` runtime permission prompt), and **Storage** (actual device
  usage via `StatFs`, with a link to the system storage settings). No
  "Mail" card — reading a real inbox needs Gmail/OAuth account integration,
  a separate project from anything a launcher can do by itself; showing fake
  mail data would be worse than not having the section.
- `ui/AppDrawer.kt` — a full-screen glass drawer drawn in the launcher's own
  window: search field, "Suggested" row of recent apps, and an A–Z rail you
  can tap or drag. Pull down at the top of the list (or press Back/Home) to
  close it.
- `SettingsActivity.kt` / `ui/SettingsScreen.kt` — Settings is a second,
  ordinary launcher-icon entry (`AndroidManifest.xml`: `LAUNCHER` category
  but deliberately *not* `HOME`), so it shows up in the app drawer like any
  other app rather than being reached through a home-screen gesture. Opening
  it lets you pick a named style preset (Zen, Pixel, Samsung, OxygenOS, Mi)
  or tune each piece yourself: accent color, icon shape/style/size, font
  size, whether home-screen icons show labels, status bar icon color, and
  which widgets are on.
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
- `ui/Wallpaper.kt` — the gradient-and-glow wallpaper from the design
  preview, with glow colors set per preset (`CustomStyle.glow*`). The glass
  surfaces blur it, so it has to have real color variation to show through.
- `widgets/Widgets.kt`'s **Glance** card shows the next alarm and battery
  level — real data needing no permissions. (Weather would need a location
  permission plus a weather API.)
- Real backdrop blur via [Haze](https://github.com/chrisbanes/haze)
  (`dev.chrisbanes.haze:haze`, pinned to `0.7.3` in
  `app/build.gradle.kts` — check Maven Central / the project's releases
  page and bump this if that version's been pulled). `Wallpaper` is the
  blur source (`Modifier.haze(state)`); widget cards, the search bar, the
  dock, the app drawer and the Settings preset rows are glass surfaces
  (`Modifier.glass` in `ui/Glass.kt`, built on `Modifier.hazeChild`). Haze
  degrades gracefully on devices without blur support (tint only).

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
  about permissions. This project requests `EXPAND_STATUS_BAR` (low-risk)
  and `READ_CALENDAR` (a dangerous permission — Play's declaration form
  will ask why; "shows the user's own upcoming events in a home-screen
  widget the user opts into" is the honest answer, and the Today panel
  works fine, just without events, if it's never granted).
- You'll need: a privacy policy URL, a feature graphic, phone screenshots,
  and a content rating questionnaire completed in Play Console.
- Double-check `applicationId` in `app/build.gradle.kts`
  (`com.zenfold.launcher`) is unique to you before your first upload — Play
  ties an app forever to whatever ID you upload first.
