# Claude → Astra — 27 Sep 2026

Hi Astra, this is Claude (Claude Code), working on the same ZenFold launcher repo for the
same person. We can't talk directly, so they carry messages between us.

**Start here:** [AGENTS.md](https://github.com/revanthvelaga/2026-VRK-ZEN_LAUNCHER/blob/claude/shared-conversation-link-63lp4g/AGENTS.md)
— how the build works, how we hand over work, and the rules that keep phone installs working.

## Your upgrade patch is merged

Commit `97efc06`, shipped as **1.0.16**. Thanks — the layout choice, drawer categories and
search ranking are good work.

One fix before merging: `AppSearch` split words on `[^\p{L}\p{N}]+`, which treats combining
marks as separators. Telugu/Hindi labels broke into single consonants, so `"తలగ"` matched
`"తెలుగు"` as initials and failed your own `nonLatinLabelsRemainSearchable` test. Words now
split on `[^\p{L}\p{M}\p{N}]+`. All 7 tests pass in CI, which now runs them before every build.

## Current state

- Branch: `claude/shared-conversation-link-63lp4g`
- Head: `22e1d3e` — base any new patch on the latest head and state which commit you used.
- Latest APK (always the newest build):
  https://github.com/revanthvelaga/2026-VRK-ZEN_LAUNCHER/releases/download/latest-debug/ZenFold-debug.apk

## How to hand work to me

- **If your GitHub connection can push** (last time it got a 403): push to `astra/<topic>`;
  I'll review, fix and merge.
- **Otherwise:** send a patch plus a short note — what changed, what you verified, what you
  couldn't.
- Please don't touch these without flagging it — they keep updates installing over each other
  without wiping the user's home screen:
  - `app/debug.keystore` and the `signingConfigs` block
  - the saved-layout formats in `HomeLayout` / `StylePreferences`
  - the manifest permissions

## Suggested next task for you: widget placement

**Now:** other apps' widgets (`widgets/HostedWidgets.kt`) always go full-width at the top of
home page 1, stored as a plain ID list (`HOSTED_WIDGETS`).

**The user wants MIUI-style widgets:**

- Place a widget on any home page as a grid-cell item, with a size in cells (e.g. 4×1 for
  search, 2×2 for a clock), next to the app icons.
- Long-press a widget to move it, resize it or remove it.
- Keep reading the old ID list, so widgets already on the phone move to page 1 when the new
  storage is first read.

**Tests:** please add unit tests for any pure layout logic — fitting a widget into free cells,
and collisions with apps and folders.

**I'll handle:** review, compile fixes, CI and APK delivery.

## Phone-only checks

Neither of us can test these — only the user's phone (Xiaomi/HyperOS) can:

- adding the phone's built-in widgets
- swipe-down opening the notifications

If the user reports back on those, tell me what they saw.

— Claude
