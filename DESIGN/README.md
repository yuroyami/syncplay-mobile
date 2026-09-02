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
- STATUS_AND_OSD: the notice queue in the room, the status line, the gesture readout in the
  notice shape.
- ROOM_CARDS: the roster rows and the panel chrome.
- CHAT: the two message shapes with grouping and the time gutter, the composer on the hairline
  field, the size floor, the fading layout on the same rows, the GIF drawer with its failure
  state.
- GESTURES: double taps accumulate into one seek, long press previews and commits once, the
  directional mark and the first-drag zone wash.
- MEDIA_INTAKE: the route sheet, the link route with recognition and the explicit failure, the
  media folders editor. Not done: the indexed file count and the lost-access state (the registry
  has no API for either yet), and the Android chooser on persistable grants.
- HOME: the form with one left edge on the hairline field, inline help and errors, the
  segmented server choice with port tags, Join as the one gradient with the shortcut glyph
  inside it, Watch alone beside it, the bar without a scaffold, notices instead of a snackbar,
  the About and tips modals, and the shortcut bugs on both platforms. The engine wheel is
  gone: every engine sits in one segmented frame with a badge square on its mark and a note
  line naming the selected one, and the TV focus helper went with the wheel, its last caller.
  After the first review on a phone: the root paints the theme's ground so no window colour
  shows through (AMOLED themes are black now), the server choice is Official, Custom or Host
  mine with hosting inline under the form instead of its own screen, the official ports are a
  segmented row, the custom fields carry a help tip instead of a help line, Watch alone lives
  only in About on its own row, the Join label is centred on the whole bar, the engine picker
  lost its dots and gained a tip per engine, and Material3 components, theme and typography are
  gone from the app: only MaterialKolor's scheme class remains, as a value holder.
  Second review: the value column has a 160dp cap instead of a weight, so the chevron stays at
  the edge when a label wraps; the stepper grows with its longest option; the media routes and
  the playlist actions fire again (their effect used to clear its own key before the launch);
  the rail runs horizontally on windows under 480dp tall instead of folding behind More; the
  status line sits beside the rail so the chat owns its corner; managed rooms are one modal
  with a segmented create-or-identify choice.
  Third review (after a file was playing): the destructive action no longer carries a weighted
  child, which used to stretch it across the whole action bar and push the other button to the
  far left (the golden now renders it); the HUD idles 5.5 s before hiding; the rail's active
  edge sits along the bottom of a row rail and the room actions start folded behind More,
  unfolding once per room session; the transport is the play key with the two jump keys under
  it; readiness is a 36dp cell; the status line sits on the top centre with the chat at 36
  percent; audio and subtitles are a two-column side panel (CardTracks) instead of a modal, and
  the picker launches straight from it; the aspect key hides on engines that cannot change it;
  seek-to is one timecode field with no title so it clears the keyboard; the undo key shows the
  position it returns to and the confirmation offers "Don't ask again"; the add-media sheet no
  longer opens by itself; the GIF drawer has an up-down type switch, a segmented source row and
  a small attribution in the grid corner; the artwork lost its caption.
  Fourth review: the seekbar's timecodes reserve the widest width their format can take, so
  the track no longer shrinks when the elapsed time crosses an hour; the undo key is a normal
  48dp key with the return time as a small badge; gestures and seek-to are side panels like
  the tracks panel (the volume and brightness tracks are gone), tool panels centre their
  titles, the tracks columns use the value size with the import and search rows first;
  notices stack on the centre line; the transport row is centre-aligned; auto-hide is a
  seconds slider where 0 keeps the controls up; chat colours and each colour open inline
  pages inside the room's settings panel (InlineEditorHost) so the chat shows every change,
  with store writes trailing the picker by 50 ms; slider rows hold the committed value until
  the store catches up; the timestamp switch, the outline switch (0 thickness is off) and the
  UI opacity row are gone; the engine's rows fold into the player category as its last group,
  with subtitle size first; the home form is centred in the height under the bar.
  Fifth review: the readiness cell measures both words and keeps one width; the tracks panel
  wraps its lists (30dp rows, one line, no index), lists the load and search rows first and
  says "Load from file"; timecodes are measured on the padded 00:00:00 format and the undo key
  widens for its badge; GIF tiles shimmer until the image reports loaded (AnimatedImage has an
  onLoaded callback on all three actuals); the managed room strings point at syncplay.pl again;
  a downloaded or loaded subtitle re-reads the track list; the room's category list runs in
  two columns; Text and Segmented can auto-size, used in the GIF drawer; add media is a side
  panel (CardAddMedia) with the link form inline; the play key and the jump keys are
  translucent; the room mark shows at 35 percent; a tool panel closes the control strip and
  the strip closes tool panels; the home form runs top to bottom with 104dp engine cells.
  Sixth review: a preference without a written summary shows none (the config default was a
  placeholder that read "OK"); the home fields keep their help line always, so focus never
  moves them; engine cells carry their badge and the "?" under the selected cell morphs into
  the story card; Trinity is Violet and PyncSlay is Neon (stored copies migrate); before a file
  loads the add key morphs in place into the routes card, the side panel stays for later;
  panels swallow taps that land between rows; route notes are one line; the tracks panel fills
  the dock again with numbered rows; a centred field style centres its text; the readiness word
  sits on the cell's centre; the playlist's header keys unfold their options in a strip under
  the header; the roster has compact, standard and by-file views behind a persisted choice.
  Adaptive home: the form is four blocks (identity, server, engine, join) placed by the window.
  Narrow: one spread column. Wide and tall (tablet, desktop): a centred two-column block with
  the join key level with the left column's foot, built on a small custom layout that hands the
  left column's height to the right one as its minimum. Wide and short (a phone on its side):
  two dense columns (no portrait spacing, full-width fields, 42dp port keys) that fit the
  height at rest, spread their blocks over the leftover height, and scroll only once something
  grows (the host panel). The help lines are one line of copy each, and the tips dialog's
  labels fit three keys at 360dp.
- THEMING: the miniature, the picker as a list with a delete that asks and works, the creator
  with a live canvas, token controls and a single-write save. Gradients stay reserved.
- SERVER_HOST: the address block with copy and share (a new platform callback on all three
  platforms), the status row with its evidence, persisted configuration rows, the log as a
  severity list with the cap, the follow rule and the errors filter, the start guard and the
  failed-start cleanup. Not done: the Android notification's client count.
- POPUPS: every popup and menu is on the modal frame; seek-to, managed room, chat colours,
  trusted domains, the playlist card's actions and URL entry, the unlock key. The old popup,
  menu, alert and multi-choice wrappers and the menu height cap are deleted.
- GLASS_SURFACES and TEXT_AND_ICONS: the tier API is the only glass entry, the panel colour
  comes from the palette, the dark pill, overlays, flexible icon and text helpers, the free
  visibility helper, the Material shape bridge and the Jost, Saira and Helvetica fonts are gone.
  The wordmark keeps its own face.
- NAVIGATION, DESKTOP_AND_KEYBOARD, TV_AND_DPAD, ACCESSIBILITY: back pops a page and asks in
  the room, pushes slide and the room crossfades, reduced motion collapses every tween from the
  platform setting or the switch, the width classes are one local, the desktop has the full key
  map with fullscreen, window memory and a minimum size, the hand cursor and hover tooltips,
  chat selection, the focus ring no longer scales, docks are focus groups, and the swipes have
  scrub rows in the gestures panel. Not done: desktop scrollbars through the frames, drop, the
  cursor hiding in fullscreen, the TV density flag.
- COPY: every settings title fits 24 characters and every summary 48, in sentence case; nine
  summaries kept their operational text as editor details; the two Android-style escapes and
  the last hardcoded English glyph names are gone; a copy lint runs with the design tests.
- PREF_SYSTEM, after the review: a row showing its explanation gets inset rules above and below
  (one between two open neighbours, none where a group rule already sits), the explanation sits
  under the label at the label's indent, long labels wrap instead of hiding, and the value column
  grows with its text up to half the row.

Still to do: the icon set (TEXT_AND_ICONS 4), the error and notice copy pass, the desktop
scrollbars and drop, the cursor hiding in fullscreen, the TV density flag, the Android server
notification's client count, the media registry's file count and lost-access state, and the
Android chooser on persistable grants.
