# Synkplay design overhaul

One folder per surface or aspect of the app. Each folder says what that thing is today, what is
right about it and must survive, what is wrong with it, the redesign, and the phases to get
there. Three root files cut across every folder:

- [INVARIANTS](INVARIANTS.md): every behaviour the code gets right that a redesign must not
  lose, with file and line. Read it before touching any surface.
- [BUGS](BUGS.md): the defects the surface audits found, with file and line. Each is fixed by
  the folder that owns its surface, as that folder lands.
- [IMPLEMENTATION](IMPLEMENTATION.md): the package map, the order of work, the commit rule and
  the verification recipe.

## Start here

[FOUNDATION](FOUNDATION/README.md) is the shared system. Every other folder assumes it and only
documents what is specific to its surface. Read it first.

## The thesis

Synkplay currently borrows Material 3's look, its dimensions and its components. That is the
root of most of the interface problems. M3 is a design system for phone operating systems;
Synkplay is a media player used in a dark room, in landscape, over video, with a thumb or a
remote.

The census as it stands in `commonMain`, 150 Kotlin files:

| | |
|---|---:|
| Files importing anything from `material3` | 45 |
| Files importing M3 `Card` | 10 |
| Files importing M3 `Button` | 9 |
| Files importing M3 `DropdownMenu` | 8 |
| Files importing M3 `AlertDialog` | 5 |
| Direct uses of `MaterialTheme.typography.*` roles | 36 |
| Distinct hardcoded text sizes (8sp to 20sp) | 13 |
| Distinct Material icons app-wide | 142 |
| Font families shipped, all in use | 5 |
| Semantics modifiers for screen readers | 0 |

And the type scale itself, in `AppTypography.kt`, describes what it is in its own comment:
"Material 3 metrics with one text family". So the Material look is not incidental, it is the
declared foundation.

The overhaul replaces the form language and keeps everything that is genuinely Synkplay's: the
theme engine, the glass surfaces, the live drawn logo, the sync behaviour, and the hard won
inset, gesture and interop fixes. Those are listed once in [INVARIANTS](INVARIANTS.md) so they
cannot be lost by accident.

## Folders

**System**

| Folder | Covers |
|---|---|
| [FOUNDATION](FOUNDATION/README.md) | space, type, colour, motion, feedback, icons, the control vocabulary, the code shape, the lint |
| [TEXT_AND_ICONS](TEXT_AND_ICONS/README.md) | the type scale, the fonts, the icon set |
| [GLASS_SURFACES](GLASS_SURFACES/README.md) | surface tiers, the glass rules, rims |
| [NAVIGATION](NAVIGATION/README.md) | the backstack, the screen frame, back handling, transitions, width classes |
| [POPUPS](POPUPS/README.md) | one modal frame for fifteen modals, and the death of dropdown menus |
| [STATES](STATES/README.md) | empty, loading, error, offline and the rest, per surface |
| [COPY](COPY/README.md) | the voice, the copy limits, the resource rules |

**Screens**

| Folder | Covers |
|---|---|
| [HOME](HOME/README.md) | the join form, the top bar, the engine wheel, watching alone |
| [PREF_SYSTEM](PREF_SYSTEM/README.md) | preferences, measured and rebuilt |
| [THEMING](THEMING/README.md) | the theme picker and creator |
| [SERVER_HOST](SERVER_HOST/README.md) | hosting a server from the device |

**The room**

| Folder | Covers |
|---|---|
| [ROOM_SHELL](ROOM_SHELL/README.md) | the frame, docks, panels, the rail, HUD timing |
| [TRANSPORT](TRANSPORT/README.md) | the one seek path, seekbar, play, ready, the 1011 line control panel |
| [CHAT](CHAT/README.md) | messages, composer, GIF panel, fading chat |
| [ROOM_CARDS](ROOM_CARDS/README.md) | people, shared playlist, panel width |
| [STATUS_AND_OSD](STATUS_AND_OSD/README.md) | the status line, notices, the gesture readout |
| [GESTURES](GESTURES/README.md) | tap, swipe and long press behaviour |
| [MEDIA_INTAKE](MEDIA_INTAKE/README.md) | getting a file into the room |

**Input and people**

| Folder | Covers |
|---|---|
| [ACCESSIBILITY](ACCESSIBILITY/README.md) | semantics, contrast, minimum sizes, gesture equivalents, reduced motion |
| [TV_AND_DPAD](TV_AND_DPAD/README.md) | focus, remote control, 10 foot density |
| [DESKTOP_AND_KEYBOARD](DESKTOP_AND_KEYBOARD/README.md) | keys, fullscreen, hover, the window |

## Order of work

The dependency order matters more than the priority order. Four things gate everything else:

1. **FOUNDATION tokens and controls** with their [accessibility semantics](ACCESSIBILITY/README.md)
   and [focus handling](TV_AND_DPAD/README.md) built in. Every surface uses these, and adding
   semantics after adoption means retrofitting every call site.
2. **The glass tiers** from [GLASS_SURFACES](GLASS_SURFACES/README.md), because glass takes its
   shape from Material's shape scale today and the shape scale cannot change until it does not.
3. **The type scale and radii** from [TEXT_AND_ICONS](TEXT_AND_ICONS/README.md). Every surface
   reads them.
4. **The screen frame and modal frame** from [NAVIGATION](NAVIGATION/README.md) and
   [POPUPS](POPUPS/README.md). Every screen and every popup sits inside one.

After that, surfaces can land in any order. A sensible sequence by visible payoff:

1. [PREF_SYSTEM](PREF_SYSTEM/README.md), because it is the measured worst and the work is already
   prototyped.
2. [TRANSPORT](TRANSPORT/README.md), because the seekbar is the most seen control in the app and
   its one seek path fixes two bugs on its own.
3. [ROOM_SHELL](ROOM_SHELL/README.md), which the other room folders build on.
4. [HOME](HOME/README.md), the first impression.
5. Everything else.

The full sequence with its gates is in [IMPLEMENTATION](IMPLEMENTATION.md).

## How this is verified

There is a headless render harness in `shared/src/desktopTest/kotlin/app/design/`. It draws
real Compose composables with no emulator and writes PNGs, which is how every measurement in
[PREF_SYSTEM](PREF_SYSTEM/README.md) was produced.

It generalises: each surface gets a golden set at 360dp, 720dp and 1200dp, at font scale 1.0 and
1.3, including every state in [STATES](STATES/README.md), so a regression that reintroduces a
seven line row or a 9sp label fails visibly instead of quietly. Four lint checks ride along as
tests, listed in FOUNDATION: no Material components outside the allowed primitives, no text
size literal outside the token file, no Material type roles, nothing under 11sp.

What the harness cannot test gets a checklist per release: screen readers, the key map, focus at
distance, and the cutout device in both orientations.

## Status

Implemented, in the order of [IMPLEMENTATION](IMPLEMENTATION.md):

- FOUNDATION: the tokens (`Space`, `Radius`, `Motion`, `Type`, `Palette`, `Tier`), the controls
  under `uicomponents/controls`, the frames under `uicomponents/frames`, and the lint ratchet.
- TEXT_AND_ICONS: the five type roles, the drawn glyph set, the Material type bridge.
- GLASS_SURFACES: `surface(tier)` and `chromeSurface`. The old glass API still exists where
  surfaces have not moved yet.
- POPUPS: `Modal` with its three sizes and the compact sheet. Older popups are migrated as their
  surfaces land.
- PREF_SYSTEM: the settings console, search, groups, the in-room panel and the engine categories.
- TRANSPORT: one seek path, the scrub track, the play key, jump keys, the control strip and its
  modals for tracks, subtitle search and chapters.
- ROOM_SHELL: `RoomFrame` docks, the rail, the side panels at a real width, HUD auto-hide behind
  its switch, PiP and solo resolved in the frame, the artwork.
- STATUS_AND_OSD: the notice queue in the room, the status line.
- ROOM_CARDS: the roster rows and the panel chrome.

Still to do: CHAT, GESTURES 3 and 4, MEDIA_INTAKE, HOME, THEMING, SERVER_HOST, the remaining
POPUPS and GLASS steps, NAVIGATION back handling and transitions, DESKTOP_AND_KEYBOARD, TV, the
ACCESSIBILITY reduced-motion switch, the COPY pass, and the items in [BUGS](BUGS.md) that belong
to those surfaces.
