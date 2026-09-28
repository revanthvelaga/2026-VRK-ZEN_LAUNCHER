# VRK Dialer — MIUI / OxygenOS-style Phone app (v0.2)

## Install
Every push to `feature/vrk-dialer` runs `.github/workflows/build-apk.yml`: it builds a
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

## Not possible for a third-party Phone app
- **Call recording**: Android blocks call audio for apps other than the phone maker's own.
- **Caller ID / spam labels** (Truecaller-style): needs a paid number database.
- **AI call summaries / live translation**: on-device features of the maker's own app.
