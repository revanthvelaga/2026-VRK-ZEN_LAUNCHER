# VRK Phone design refresh

Based on feature/vrk-dialer at 0bd7a64.

The home screen now opens on call history with bottom navigation for Calls, Contacts,
and Favourites. An explicit Keypad action opens the T9 dial pad. The same warm neutral
surfaces, emerald palette, typography, and rounded geometry carry through contact
details, settings, and calls. Dark mode follows the system. Wallpaper colours no longer
change the call interface. The app has an adaptive launcher icon and the label VRK Phone.

## Behaviour

- Calls support name/number search and a missed filter, with date sections.
- Contacts retain alphabetical sections, search, contact details, and direct calling.
- Favourites use adaptive cards with visible contact-details actions.
- Keypad retains tones, haptics, T9, long-press 0 for +, voicemail and speed dial.
- An empty keypad can retrieve the last number without placing an accidental call.
- Back closes and clears the keypad, then search, then returns to Calls.
- Short windows can scroll the keypad and call controls instead of clipping them.
- Existing Telecom services, dual-SIM choices, blocking and notification logic remain.
- Contact photos reset when a recycled row changes to a different photo or no photo.

## Validation

`gradle assembleDebug lintDebug testDebugUnitTest`

Robolectric UI tests exercise missed filtering, contact search, keypad entry and the
outgoing-call callback. They also render light/dark screenshots with fictitious contacts
under `app/build/outputs/design/`. The dial callback is intercepted; tests place no calls.

Before daily use, validate on a physical phone: incoming calls while locked, answer and
reject, Bluetooth/speaker/proximity, dual-SIM selection, voicemail, speed dial, larger
font sizes, rotation, notification permission denial, and returning after a call ends.
A UI refresh does not establish parity with Google Phone's spam identification or
carrier/device integrations.
