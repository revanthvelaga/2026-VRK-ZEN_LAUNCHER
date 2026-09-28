# VRK Phone: premium themes

Built on 3bd9573, preserving Claude's contact-photo, keypad placement, launcher-icon and sound fixes.

## Implemented
- Appearance → preview and apply Sapphire, Flow or Luminous. SharedPreferences stores stable theme IDs and system/light/dark mode. Both activities observe updates.
- Sapphire: pearl/sapphire palette and sculpted keys. Flow: violet palette, open keys, recent-call shelf, hide/show keypad, two explicit SIM call buttons when available, portrait-led caller screens. Luminous: mint/emerald palette, rounded keys and dark green surfaces.
- Consistent shared theme across tabs, details, settings, dialogs and in-call screens. Safe green/red call semantics.
- Photo favourites with call/message shortcuts and app-only favourite additions; synced favourites are also displayed.
- Per-number private notes stored locally; calendar reminder draft (user chooses time and saves in installed calendar app).
- Keypad SIM selector uses actual Telecom accounts; no invented carrier labels. System preference and ask-each-time still supported.
- Independent calls/contact searches, T9 matching, paste, delete/clear, voicemail and speed dial retained.
- Touch sound, vibration and decorative motion switches.
- Corrected incoming swipe direction in prior branch: up answers, down declines. Short swipes cancel, threshold haptic, one action per control, TalkBack custom actions. Reset control state when call identity changes.
- Workflow uploads actual Robolectric render images and test/lint reports alongside existing APK release flow.

## Verification
Tests cover theme preview/apply, stored values and recovery, dialing in six theme/mode combinations, collapse/expand, incoming gesture direction and duplicate activation. Existing calls, filters, tab swipes and small-window coverage retained.

Physical-device acceptance remains necessary: incoming calls locked/unlocked, dual SIM, call waiting, Bluetooth, proximity, conference, large fonts, and interrupted gestures. Robolectric cannot certify carrier/Telecom behavior.

## Deliberate scope
Themes are original VRK designs inspired by the approved concepts, not exact reproductions of any vendor phone app. No AI caller identification, call recording, paid subscription, or unsupported video calling is advertised. Calendar reminders use the user's calendar rather than an unverified background alarm service. No stock portrait photos are bundled; the app uses the user's contact photo or initials.
