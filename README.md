# VRK Dialer — MIUI-style Phone app (v0.1)

## Build
Every push to `feature/vrk-dialer` runs `.github/workflows/build-apk.yml`:
it builds a debug APK and publishes it under **Releases** as `dialer-build-N` (pre-release).
Open that release on your phone, download the APK, install, and accept
"Set as default Phone app".

Local build: open the folder in Android Studio (JDK 17) and Run.

To go back: Settings → Apps → Default apps → Phone app → Phone by Google.

## What works
- Dialpad with T9 search (numbers, names, initials), long-press 0 for +, long-press ⌫ to clear
- Recents (grouped, missed in red) + Contacts tabs, keypad hides on scroll
- Dual-SIM picker when phone is set to "ask every time"
- Incoming/outgoing call screen: answer, decline, mute, speaker, hold, keypad (DTMF), timer
- Proximity sensor turns screen off at your ear

## Not yet (next steps)
- Call waiting / conference (v1 tracks one call)
- Heads-up notification for incoming calls while using another app + ongoing-call notification
- Bluetooth audio route button
- Contact detail page, favourites, block list
- Call recording: not possible for third-party apps on modern Android
