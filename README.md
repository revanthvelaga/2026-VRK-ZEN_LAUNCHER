# VRK Phone — premium dialer design (v0.4)

A native Android dialer with a consistent emerald and neutral design, light/dark
themes, searchable call history, contact cards, and an on-demand keypad.
See [design and validation notes](docs/PREMIUM-DIALER.md).

## Install
Once these changes are applied to `feature/vrk-dialer`, every push runs `.github/workflows/build-apk.yml`: it compiles, lints, runs UI tests, and builds a
debug APK (shrunk to a few MB) and publishes it. The newest build is always at:

https://github.com/revanthvelaga/2026-VRK-ZEN_LAUNCHER/releases/download/dialer-latest/VRK-Dialer.apk

Open that on your phone, install, and accept "Set as default Phone app". Every build is
signed with the committed debug key (`app/debug.keystore`), so updates install over the
previous one. Each build is also kept as its own `dialer-build-N` pre-release.

To go back: Settings → Apps → Default apps → Phone app → your old Phone app.

## Features
**Calls**
- Incoming call: heads-up with Answer / Decline while you use the phone, full screen when
  locked; **decline with a message** (editable quick responses, or write your own)
- Ongoing-call notification with a live timer and Hang up
- **Call waiting**: Decline, Hold & answer, or End & answer
- Hold / resume, **swap** between two calls, **merge** into a conference, manage the
  conference (split someone off privately, or drop them)
- Audio: speaker toggle, or Bluetooth / headset / speaker / phone picker when connected
- Mute, in-call keypad (DTMF), dual-SIM label, proximity screen-off at your ear

**Dialer**
- Keypad with T9 search (names, numbers, initials), dial-pad tones (follows the phone's
  setting), long-press 0 for +, long-press ⌫ to clear, empty + call = last number
- **Speed dial**: long-press 2–9 (assign in Settings or on first long-press); long-press 1
  = voicemail
- Typed number that isn't a contact: Create new contact, Send message
- Dual-SIM picker when the phone is set to "ask every time"

**Recents, Contacts, Favourites**
- Recents: All / Missed filter, grouped repeats "(3)", call type icons, SIM name,
  tap to call back, ⓘ or long-press for details; opening Recents clears the missed-call alert
- Contacts: search, A–Z sections, contact photos, quick call button
- Favourites: your starred contacts as a photo grid, tap to call

**Number details**
- Call, Message, Copy, open or save the contact
- Full call history with date, duration and SIM; delete this number's history
- Block / unblock (the phone's own block list)

**Settings**
- Default Phone app status, SIM & call settings (forwarding, waiting, caller ID),
  blocked numbers, call notification settings, quick responses, speed dial

## Design and interaction (v0.4)
- **Visual system**: a stable emerald palette, warm neutral surfaces, deliberate
  typography, adaptive icon and system-following light/dark themes.
- **Navigation**: Calls, Contacts and Favourites in bottom navigation; a prominent
  keypad action moves into the header on short screens.
- **Search and history**: name/number search, missed-call filtering, date sections.
- **Keypad**: shaped keys, number paste, labelled call action and scrollable controls
  when the available screen height is limited.
- **Contact cards**: adaptive favourites grid and redesigned contact details.
- **Motion**: a soft pulsing ring behind the avatar on an incoming call, spring-loaded
  press feedback on every key and round button, and a sliding crossfade between Home,
  Details and Settings instead of a hard cut.
- **Efficiency**: the in-call timer now ticks only the one line of text that shows it
  (previously the whole call screen recomposed every second, even while ringing); T9
  contact matching is pre-indexed once per contact-list change instead of being
  recomputed on every keystroke; Recents/Contacts refresh the moment the call log or
  contacts actually change (a debounced `ContentObserver`), not only when you return
  to the screen.
- **Robustness**: fixed an incorrect `Call.Details.can(...)` call (it's a static method,
  not an instance one — merge/conference availability would have failed to compile,
  since caught and corrected); shrunk build now carries defensive ProGuard keep rules
  for the two system-invoked components (`CallService`, `CallActionReceiver`).

## Not possible for a third-party Phone app
- **Call recording**: Android blocks call audio for apps other than the phone maker's own.
- **Caller ID / spam labels** (Truecaller-style): needs a paid number database.
- **AI call summaries / live translation**: on-device features of the maker's own app.
