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
  via `PackageManager`; `ui/InstalledApps.kt` keeps that list live
  (`LauncherApps.Callback`), so apps installed or uninstalled while the
  launcher is running appear/disappear without a restart.
- `home/HomeLayout.kt` — Home as an explicit list the user owns, spread over
  as many **pages** as it needs: each cell holds an app or a **folder**, and
  it's never "full" — a new app goes to the first free cell, on a new page
  if necessary. The grid size is adjustable (Settings → Home grid: 4–5
  columns, 4–6 rows); apps that no longer fit move to the next free spot.
  Nothing appears on Home unless you put it there (the one exception: the
  first launch seeds it with your default gallery/maps/music/calendar...
  apps). Every edit is a pure function over that list, applied atomically
  inside one DataStore transaction (`StylePreferences.editHomeItems`), so
  quick successive edits never overwrite each other.
- `ui/HomeScreen.kt` — the pager (Today, home pages, all apps), MIUI-style:
  page dots over a plain dock row at the bottom, no search bar. The **dock**
  is yours to edit — long-press any app → **Add to dock** (up to 5), or a
  dock app → **Remove from dock**. Long-press and drag an icon: onto an
  empty cell to move it, **onto another app to make a folder** (auto-named
  from the apps' shared Play Store category — "Games", "Social &
  Communication"... — else "Folder"), onto a folder to add it, **to the top
  to Remove or Uninstall** it, or **to the screen's edge** to send it to the
  previous/next page (a new one if needed). Folders (`ui/Folders.kt`) show a
  2×2 preview of their first four apps; tap to open (they zoom open), tap
  the title to rename. Long-press opens the menu straight away (it closes
  if you start dragging): the app's own **shortcuts** ("New message",
  "Compose"...), **Remove**, **Select**, Add to dock, **App info** and
  **Uninstall** (hidden for built-in apps, which can only be disabled).
  **Select** ticks icons: tap more, then **Remove** them all or group them
  into a **Folder**. Uninstall needs `REQUEST_DELETE_PACKAGES` — without it
  Android 9+ silently refuses to show the uninstall dialog. **Long-press empty space** for the home menu
  (`ui/HomeEditing.kt`): **Add apps** (every app with a checkbox — ticked =
  on Home), **Widgets**, **Settings**. Swipe **down** for the notification
  shade, **up** for the all-apps drawer — or tap the **^** above the dock,
  or swipe up from the dock (which works even on a page whose own content
  scrolls).
- **App pages (MIUI-style)** — keep swiping right past your home pages and
  every app that isn't on Home or in the dock is laid out A–Z, a full grid
  per page (as many rows as fit the screen). So each app lives in exactly
  one place: **Add to Home** moves it off the app pages, and removing it
  from Home puts it back — nothing gets lost. The dock and page dots stay
  put across Home and the app pages; Back returns to Home.
- **Motion** — icons sink under your finger and spring back (no ripple),
  apps open zooming out of the tapped icon (`ActivityOptions`
  scale-up + `sourceBounds`), home pages shrink/dim slightly while you
  swipe between them, folders and sheets spring open, and Home's icons zoom
  back in when you return from an app.
- **Notification dots** — `notifications/ZenFoldNotificationListener.kt`
  publishes which apps have dot-worthy notifications (skipping ongoing ones
  like music, and channels marked "no dot"), shown on icons in Home,
  folders, the dock and the drawer. Needs notification access — Settings
  shows whether it's on and links to the system toggle.
- **Other apps' widgets** — `widgets/HostedWidgets.kt` + an `AppWidgetHost`
  in `MainActivity`: Home menu → Widgets lists every app that offers
  widgets (clock, weather, Google search, Cricbuzz...), like Android's own
  picker — tap an app to see its widgets' **preview images** and grid size
  ("2 × 1"), tap a preview to add it. Previews load only when an app is
  opened, so the sheet opens instantly. Android asks once per provider for
  permission to bind it, then runs the widget's own setup screen if it has
  one; added widgets sit full-width on the first home page and can be
  removed from the same sheet.
- **Icon packs** — `icons/IconPacks.kt` finds installed packs (the standard
  ADW/Nova/Apex theme intents) and reads their `appfilter.xml`; pick one in
  Settings → Icon pack and its icons replace each app's own wherever the
  pack has one.
- **Double-tap to lock** — double-tap empty Home space to turn the screen
  off, via `system/LockScreenService.kt`, an accessibility service that
  reads nothing and only performs the lock action (the alternative, device
  admin, would disable fingerprint/face unlock on the next wake). Off until
  turned on in Android's Accessibility settings; Settings → Double-tap to
  lock links there.
- `ui/AppActions.kt` — the shared long-press menu. App shortcuts come from
  `LauncherApps`, which Android only lets the *default* Home app read — so
  they appear once ZenFold is set as your launcher.
- `ui/TodayPanel.kt` — the page to the left of home: a time-of-day
  greeting; a row of stat tiles (**Gold**, **Sensex** with a green/red
  change, and device **Storage**); a **cricket scoreboard** (a LIVE badge,
  each team's runs/wickets/overs, the match status); a real **Calendar**
  month grid (prev/next-month arrows, a dot on days with events, tap a day
  for its events — `CalendarContract.Instances`, behind the
  `READ_CALENDAR` prompt); **Tasks** (persisted by
  `tasks/TaskPreferences.kt`); and **Trending** — the top 10 things people
  in your country are searching for right now, each with its lead headline.
  All of it is live with **nothing to set up** — no accounts or API keys —
  and refreshes every two minutes while the page is open; a feed that
  can't be reached keeps its last good value. Tap a tile, match or trend
  to open it. No "Mail" card — reading a real
  inbox needs Gmail/OAuth account integration, a separate project from
  anything a launcher can do by itself; showing fake mail data would be
  worse than not having the section.
- `feeds/` — `FeedApi.kt` makes the network calls (OkHttp; `org.json` and
  Android's XML pull parser), all to public, keyless feeds, straight from
  the device: **gold** and **Sensex** from Yahoo Finance's public chart
  endpoint (COMEX gold × USD→INR, per gram; `^BSESN`), **cricket** from
  ESPNcricinfo's live-scores RSS, and **trending** from Google Trends'
  "trending now" RSS for the phone's country (network, then SIM, then
  language setting). `FeedModels.kt` has the plain data classes. These are
  unofficial public feeds meant for personal use — fine for your own
  launcher, but check each provider's terms (or switch to a licensed data
  API) before shipping this to other people. Pixel's "Google feed" (Discover)
  isn't available: Google only lets its own approved launchers embed it.
  X/Twitter has no free feed at all (its API is paid), which is why the
  Trending card uses Google Trends instead.
- `ui/AppDrawer.kt` — the all-apps drawer (swipe **up** on Home or an app
  page; it slides up over the page): search field, "Suggested" row of recent apps, and an A–Z
  rail you can tap or drag. Long-press any app for its shortcuts plus **Add
  to Home** and **Hide app** (hidden apps leave the drawer, search and Home;
  unhide them in Settings → Hidden apps). Pull down at the top of the list
  (or press Back/Home) to return to Home.
- `SettingsActivity.kt` / `ui/SettingsScreen.kt` — Settings is a second,
  ordinary launcher-icon entry (`AndroidManifest.xml`: `LAUNCHER` category
  but deliberately *not* `HOME`), so it shows up in the app drawer like any
  other app rather than being reached through a home-screen gesture. Opening
  it lets you pick a named style preset (Zen, Pixel, Samsung, OxygenOS, Mi)
  or tune each piece yourself: accent color, icon shape/style/size, font
  size, whether home-screen icons show labels, status bar icon color, which
  widgets are on, home grid size, icon pack, double-tap to lock, and
  hidden apps.
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
  about permissions. This project requests `EXPAND_STATUS_BAR` (low-risk),
  `INTERNET`/`ACCESS_NETWORK_STATE` (low-risk — used only for the Today
  page's public market, cricket and trends feeds), and
  `READ_CALENDAR` (a dangerous permission — Play's declaration form
  will ask why; "shows the user's own upcoming events in a home-screen
  widget the user opts into" is the honest answer, and the Today panel
  works fine, just without events, if it's never granted).
- The double-tap-to-lock **accessibility service** needs Play's
  AccessibilityService declaration plus an in-app disclosure before the
  user enables it. Launchers using it only for screen lock are accepted,
  but expect review questions — or drop `system/LockScreenService.kt` and
  its manifest entry for the first release.
- You'll need: a privacy policy URL, a feature graphic, phone screenshots,
  and a content rating questionnaire completed in Play Console.
- Double-check `applicationId` in `app/build.gradle.kts`
  (`com.zenfold.launcher`) is unique to you before your first upload — Play
  ties an app forever to whatever ID you upload first.
