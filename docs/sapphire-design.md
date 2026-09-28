# Sapphire phone experience

The approved Sapphire concept is implemented with native Compose controls and actual device contacts; placeholder identities and the mock profile from the concept image are not inserted into the app.

- Four persistent tabs: Keypad, Calls, Contacts, Favourites. HorizontalPager handles drag tracking, settling, cancellation and animated tab selection.
- Sapphire/lavender light palette and coordinated dark palette; circular keypad, green call action, tinted favourite cards and floating navigation.
- Recent row tap opens details. The independent green call icon calls immediately. Details show the loaded individual call records, dates, durations, account labels and total duration. The existing call-log loader limits the history snapshot to the latest 1,000 device records.
- Incoming call: swipe green handset up to answer, red handset down to decline. Active call: swipe red handset down to end. Drag 64dp and release to commit; wrong-direction, short and cancelled drags do not commit. A control commits once per composition. TalkBack exposes an activation action.
- Ringing handset rocks visually; threshold crossing and activation provide haptic feedback. Keypad and round controls have spring press feedback. System animation-duration settings apply to Compose animations.
- Existing T9 contact lookup, voicemail, speed dial, paste, clear number, call/message shortcuts, blocking, SIM selection and call-history deletion remain available.

## Validation

Run `gradle testDebugUnitTest assembleDebug lintDebug` with JDK 17 and Android SDK 35. UI tests cover entered-number calling, missed-call filtering, contact search, favourites, short windows, swipe tab navigation, recent-row details, and gesture thresholds. Rendered test captures are written to `app/build/outputs/design`.

Physical-device checks still required: incoming call while locked, answer/decline/end, caller waiting, speaker/Bluetooth, proximity sensor, dual SIM, TalkBack, vibration preferences, large font and reduced animations. These cannot be proved by a visual preview or Robolectric.
