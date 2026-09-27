# Working on ZenFold with more than one AI assistant

ZenFold is worked on by more than one AI assistant (Claude Code, and others such as
Astra) for one owner. None of them can talk to each other directly — **this repository
is the shared workspace**. Read this file first, then `README.md` for the features.

## The project in one paragraph

An Android home-screen launcher in Kotlin + Jetpack Compose (single `app` module,
package `com.zenfold.launcher`). MIUI-inspired: Today page on the left, home pages with
folders and a dock, MIUI-style app pages to the right, a swipe-up A–Z drawer, other apps'
widgets via `AppWidgetHost`, the phone's wallpaper, and keyless live feeds (gold,
Sensex, cricket, Google Trends). There is no local Android SDK in the assistants'
sandboxes: **GitHub Actions is the compiler and test runner**.

## How a change reaches the owner's phone

1. Commit to the development branch `claude/shared-conversation-link-63lp4g`
   (or hand over a patch — see below).
2. GitHub Actions (`.github/workflows/build-debug-apk.yml`) runs
   `./gradlew testDebugUnitTest assembleDebug`.
3. On success it replaces the APK on the rolling `latest-debug` release. The owner
   installs from one fixed link:
   https://github.com/revanthvelaga/2026-VRK-ZEN_LAUNCHER/releases/download/latest-debug/ZenFold-debug.apk
4. The version is `1.0.<Actions run number>`, visible in Android App info.

## Handing work to each other

- **If you can push:** use your own branch (e.g. `astra/<topic>`) off the latest
  development branch, and describe the change in the commit message. The other
  assistant reviews it, fixes what CI or review finds, and merges it into the
  development branch.
- **If you can't push:** produce a `git diff` / patch against the **current head**
  of the development branch, state that commit hash, and say what you did and did
  not verify (compiled? tests run? tried on a phone?). The owner uploads it to the
  other assistant, which reviews, applies, builds and ships it.
- Leave notes for the next assistant in the commit message or at the end of this
  file under "Open threads" — not in chat, which the other assistant can't see.

## Rules that keep the phone install working

- **Never change or delete `app/debug.keystore` or the `signingConfigs` block.** Every
  build must be signed with that key, or Android refuses to install it over the
  previous one (the owner then loses their layout to a reinstall).
- Don't rename DataStore keys in `style/StylePreferences.kt` or change
  `HomeLayout.encode`'s format without reading the old one too — that's the owner's
  saved home screen.
- Keep `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES`, `EXPAND_STATUS_BAR` and the
  `windowShowWallpaper` theme: uninstall, widgets, swipe-down and wallpaper depend on
  them.
- Any pure-Kotlin logic you add should get a unit test in `app/src/test/` — CI runs
  them before building. Say plainly what you could not verify.
- Match the surrounding code: comments explain *why*, not *what*; no new libraries
  without a reason stated in the commit.

## Open threads

- Phone-only checks nobody can automate here: widget adding on Xiaomi/HyperOS, the
  swipe-down shade on the owner's ROM, and wallpaper parallax.
