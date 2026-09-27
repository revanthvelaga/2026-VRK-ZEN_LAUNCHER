# Astra → Claude — 28 September 2026

Read your handoff and AGENTS.md. Thank you for catching the combining-mark/initials bug and shipping the first patch as 1.0.16.

## User's expanded instruction

The owner is dissatisfied with a small usability patch. They want a coherent premium-quality design covering Home, notification experience, icons, themes, wallpapers, Phone and Messages, benchmarked against paid launchers as well as Samsung/Xiaomi/OPPO/Huawei. They explicitly asked me to interact with you. There is no direct Claude channel; this note and patch are the handoff, not a claim that you have reviewed them.

## Base / branch

Base: `278979641f891dffd7cf958d0adc8bbf77088e80` from `claude/shared-conversation-link-63lp4g`.
Local branch: `astra/design-studio`.
I rechecked GitHub writes: create-blob still returns 403 Resource not accessible by integration. Please review and apply the attached patch on the current development branch, resolving newer work rather than overwriting it.

## Native implementation

- Original coordinated Dune, Orbit, Moss and Porcelain palettes.
- Settings → Design Studio, preview/cancel/apply, optional matching home artwork and theme icons.
- Apply theme in one DataStore transaction while preserving navigation preferences.
- New `wallpaper_art` and `clock_design` keys only; defaults preserve old rendering.
- Original procedural Dune/Orbit/Mist canvas art; no new image/network dependencies.
- Tonal icon mode: native monochrome glyph where available, grayscale original fallback.
- Classic/editorial/stacked clock with locale and 12/24-hour support.
- Material light palette handling for Porcelain.
- Optional grouped notification inbox from the existing listener; hidden apps excluded, content hidden by default, opens app rather than pretending direct reply exists.
- Home signal closes inbox. Android's notification shade remains intact.

## Design / benchmark material

`COMPETITIVE-REVIEW.md` compares nine product families using official pages, including paid Niagara Pro and Nova Prime. It specifies the full suite and acceptance gates. `zenfold-concept.html` is the interactive six-screen concept; Phone and Messages are explicitly labeled nonfunctional companion-app concepts. Do not ship them as if they were actual dialer/SMS implementations.

## Verification and limitations

- `git diff --check` passes.
- Independent sRGB calculations: the minimum normal-text contrast on the four palettes' opaque background/surface pairs is 5.52:1; this does NOT certify text over every arbitrary wallpaper.
- Added three JVM tests for stable theme IDs, navigation preservation and >=4.5:1 opaque text contrast.
- Android code not compiled here: no Android SDK; GitHub Actions is the project's build/test path and upload is blocked.
- Browser rendering/interaction verification status is recorded in VALIDATION.md. Do not infer phone behavior from an HTML concept.
- No real-device testing. In particular check stacked-clock height, 200% fonts, Porcelain surfaces, old/new icon packs and Xiaomi widget/gesture behavior.

## Explicitly unchanged

Debug signing/key, manifest permissions, HomeLayout encoding, existing preference keys, widget ID storage and workflow. New preferences are additive; unknown enums use existing fallback behavior. No new dependencies.

## Requested review/build

1. Review patch against latest development HEAD; apply on a review branch.
2. Run `./gradlew testDebugUnitTest assembleDebug` and fix compilation/test issues.
3. Inspect theme preview/apply, light surfaces and tonal fallback before merge.
4. Verify hidden-app notifications and permission denial; test a Home press from the inbox.
5. Publish through the existing signed debug workflow; report exact run/commit/version to the owner.
6. Request device screenshots and gesture/widget results before claiming polish/performance.

## Next collaboration

Your widget-placement proposal is still the next infrastructure priority. I deliberately did not change widget persistence while doing this design pass. Please take the grid occupancy + migration work, or send a focused handoff so we avoid conflicting format changes. Keep Phone/Messages as separately scoped Android components with platform-role requirements. Please reply in a repo handoff note with review findings and the built APK version.
