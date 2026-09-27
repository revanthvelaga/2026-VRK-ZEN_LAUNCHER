# ZenFold: competitive review and product direction

Reviewed 28 September 2026 (India). Repository baseline: `278979641f891dffd7cf958d0adc8bbf77088e80`.

## What this comparison establishes

This is a review of official product documentation and the ZenFold source, not a hands-on performance test or an exhaustive ranking of every launcher. Paid features are included as design benchmarks. Prices are omitted because they vary by region and offer. No claim is made that ZenFold currently beats these products.

| Benchmark | Documented strength | What ZenFold should learn | Present gap / decision |
|---|---|---|---|
| Niagara Pro | Complete themes, icon assistance, custom fonts, calendar/weather, popup folders, widget stacks | Treat a setup as a coordinated composition; keep frequent actions close | Design Studio begins coordinated theming; widget stacks, per-icon overrides and custom fonts remain open |
| Nova / Nova Prime | Desktop/drawer/dock/folder customization, backup/restore; Prime gestures, badges and drawer folders | Preserve user control and make experimentation reversible | Gestures and dots exist; portable backup/restore and drawer folders are still missing |
| Smart Launcher | Fluid grids, wallpaper-derived colors, modular pages, automatic categories, precise widget positioning | Icons and widgets must align; customization should require little effort | Drawer categories exist; true grid widgets and wallpaper-color extraction remain missing |
| Lawnchair | Pixel-style design, Material You, At a Glance, optional QuickSwitch integration | Familiar interactions and restrained design; do not imply privileged recents are universal | Maintain standard Home behavior; recents integration is not part of this patch |
| Microsoft Launcher | Calendar/tasks/notes feed, layout import, daily Bing wallpaper | Make the left page useful rather than a feed of unrelated numbers | Existing Today page has tasks/calendar; configurable cards and feed removal should precede more feeds |
| Samsung One UI | Home widgets, widget stacks, adaptable layouts; vertical alphabetical apps and bottom search | Reachability, consistent spacing, predictable editing | Bottom search exists; stacks and grid widgets require further work |
| Xiaomi HyperOS 3 | Coordinated icons, home widgets, wallpaper/lock-screen personalization and system motion | Cohesive materials across screens; motion follows direct manipulation | Home art and themes are implementable; lock screen and system task transitions are not ordinary launcher surfaces |
| OPPO ColorOS 16 | Luminous icon treatment, Flux Themes, depth wallpaper and system-wide animation | Choose a distinct material and typography system, not a bag of copied controls | Original tonal icons/artwork in this patch; no claim of ColorOS rendering-engine parity |
| Huawei service widgets | Glanceable information and app actions in multiple home-widget layouts | A widget should save an app launch and fit its surrounding grid | Standard Android AppWidgetHost support exists; Huawei service widgets are not a portable third-party API |

## Product goal

An original, free and ad-free launcher that feels composed on first use and remains controllable after months of use. Quality is measured by successful everyday tasks, legibility, predictable state and measured performance—not the number of toggles. Brand skins are references, not assets to copy or a claim of affiliation.

The visual direction is warm editorial typography, quiet backgrounds, restrained depth, recognizable icons and reachable controls. Four families cover different preferences: Dune (warm dark), Orbit (blue dark), Moss (natural dark), Porcelain (light). The native patch introduces these families without resetting the user's arrangement.

## Scope for everything the owner requested

| Surface | Intended experience | What an ordinary launcher can deliver | Status in this package |
|---|---|---|---|
| Home | Distinct clock, readable labels, calm dock, notification entry, reachable search | Full control over its own home UI | Native clock/theme/inbox entry implemented; general layout engine retained |
| Icons | Original, crystal or tonal; consistent shape; preserve recognizability | Render installed icons and supported monochrome layers | Tonal mode added; older icons use a grayscale inset fallback |
| Wallpaper | Curated art, matched color palette; own photo option | Draw launcher art or invoke the system wallpaper picker | Three new procedural artworks; existing glow and phone wallpaper retained |
| Themes | Preview before applying; no layout loss | Coordinate launcher colors, clock, icon treatment | Native Design Studio with apply/cancel and optional wallpaper/icon changes |
| Notification bar/shade | Clear priority, quick access, content privacy | Open Android's shade; build a separate consent-based inbox | Grouped native launcher inbox, hidden content by default; system shade not restyled |
| Phone | Clear keypad, contacts/recents, dual-SIM choice, dependable in-call UI | Launch existing dialer now; full replacement requires a separate dialer implementation | Interactive visual concept only; no calling functionality added |
| Messages | Readable threads, clear delivery state, compose, accessibility | Launch existing messaging app; full SMS/MMS replacement needs its own implementation and role | Interactive visual concept only; no SMS access or sending added |
| Widgets | Place, move, resize and stack alongside icons | Standard Android widget hosting with provider constraints | Existing full-width hosting unchanged; next infrastructure priority |
| Folders/drawer | Fast app finding, adjustable density, reliable gestures | Fully implementable locally | Earlier shipped upgrade retained; no fabricated new claims |
| Lock screen / recents | Matching art and fluid transitions | Some wallpaper changes possible; privileged system UI isn't replaced by a Home app | Out of native patch; retain OS behavior |
| Accessibility | TalkBack, large fonts, RTL, reduced motion, 48dp controls | Must be designed and tested throughout | Opaque palette contrast checked; full accessibility/device audit remains necessary |

## Detailed design contracts

### Home and motion

- Keep top space for orientation and glanceable information; put frequent controls near the bottom.
- Use 4/8dp spacing increments and 20–28dp panel radii. Labels stay legible over busy photos; test an independent scrim, not only curated art.
- Clock uses the device's 12/24-hour preference and locale. Editorial and stacked variants are options, not forced migrations.
- Avoid decorative perpetual animation. Touch feedback must be interruptible; system reduced-motion settings must be verified before calling motion complete.
- User-added widgets must eventually participate in the same occupancy model as apps and folders. No layout format changes in this patch.

### Theme Studio and wallpapers

- Browse named looks; use a live palette/artwork preview. Cancel performs no preference writes.
- Applying commits style and optional wallpaper/icon-pack changes in one DataStore transaction.
- Preserve home items, dock, hidden apps, drawer density and gesture preferences.
- Future: import/export theme recipes, a restore-last-look action, wallpaper color extraction, per-app icon overrides and licensed photo collections. Do not advertise any as implemented.
- Current procedural art is original, offline and resolution independent. It is launcher artwork, not a system lock-screen wallpaper setter.

### Notifications

- Keep Android's pull-down shade and system controls. ZenFold Inbox is a separate surface.
- Group live listener entries by app. Never manufacture unread counts from a demo.
- Hidden apps are excluded. Message content starts hidden and is revealed only in the open inbox.
- Permission-denied and no-notifications states are first-class states. Tap opens the app, not an unimplemented direct-reply flow.
- Next: PendingIntent deep links, safe dismiss, supported RemoteInput reply, ranking-aware groups, redaction on lock/profile restrictions. Review listener data handling before adding these.

### Phone companion

Visual concept includes a reachable keypad, large primary call action and editable digits. Production scope requires contact search, recents, incoming/ongoing-call screens, call waiting, mute/speaker/hold, SIM selection, Bluetooth routing, emergency-call behavior and accessibility. Implement through Android Telecom/InCallService and default-dialer role, not as a pretty screen that cannot handle calls. The current prototype places no call.

### Messages companion

Visual concept uses clear sender identity, left/right message groups, a fixed compose area and coherent theme colors. Production scope requires real thread storage, receive/send/delivery/failure states, dual-SIM, multipart messages, MMS, attachments, draft recovery, contact lookup, default-SMS role and permission review. Do not claim RCS access; it requires a separately supported integration. The prototype sends nothing.

## Delivery sequence and acceptance gates

1. **Design foundation (this patch):** native theme studio, tonal icons, clock/artwork, launcher inbox and complete visual direction. Compile and execute tests before merging; install over 1.0.16+ and verify saved layout.
2. **Widget infrastructure:** app/folder/widget collision model; placement on every home page; resize using provider min/max sizes; migration of existing hosted IDs; unit tests for migration, collision and repacking. Coordinate this with Claude before changing persistence.
3. **Daily reliability:** backup/restore, layout lock, undo for removal, per-icon customizations, work-profile handling, configurable Today cards and permission-denial recovery.
4. **Measure experience:** cold/warm Home startup, drawer/search frame timing, memory with 200 apps, standby battery, rotation, 200% font scale and TalkBack. Test both a mid-range phone and the owner's actual phone. Targets (not measured claims): <=500ms warm usable Home; 95th-percentile frame time under the active refresh budget on an agreed test scenario; no network required for app launch/search/themes.
5. **Companion apps:** implement Phone and Messages independently with the same design tokens. Their completion is not a prerequisite for a stable launcher release, but they are required to fulfill a coherent suite across those interfaces.

## Official sources

Accessed 28 September 2026 (India).

- Niagara Pro: https://help.niagaralauncher.app/article/40-niagara-pro-features
- Nova / Prime: https://www.novalauncher.com/
- Smart Launcher: https://www.smartlauncher.net/
- Lawnchair: https://lawnchair.app/
- Microsoft Launcher: https://support.microsoft.com/en-us/office/using-microsoft-launcher-on-android
- Samsung widget behavior: https://www.samsung.com/ca/support/mobile-devices/use-widgets-on-your-galaxy-phone/
- Samsung app drawer: https://www.samsung.com/lb/support/mobile-devices/guide-to-apps-screen-changes-on-the-samsung-galaxy-devices/
- Xiaomi HyperOS: https://www.mi.com/global/hyperos
- OPPO ColorOS 16: https://www.oppo.com/en/coloros16/
- Huawei service widgets: https://consumer.huawei.com/en/support/content/en-us15915539/
- Android dialer requirements: https://developer.android.com/develop/connectivity/telecom/dialer-app
- Android SMS intents: https://developer.android.com/guide/components/intents-common
- Android SystemUI ownership: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/statusbar/phone/CentralSurfacesImpl.java
