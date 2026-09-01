# Room panels overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md) and [ROOM_SHELL](../ROOM_SHELL/README.md).

Files: `room/ui/rightcards/RoomSectionSlidingCards.kt` (113),
`room/ui/rightcards/CardUserInfo.kt` (156), `room/ui/rightcards/CardSharedPlaylist.kt` (567),
`room/ui/rightcards/CardRoomPrefs.kt` (97).

## What it is

The three sliding panels: who is in the room, the shared playlist, and in-room settings. They
are mutually exclusive and slide in from the right edge.

## What is right already

- Mutual exclusion is enforced in the state manager, not the UI: opening one closes the other
  two, and only when turning on.
- The playlist's file pickers launch only after their menu has finished dismissing, or iOS
  crashes with "Already resumed" (the FileKit rule), and the shuffle flag is set before the
  picker fires because the callback consumes it.
- The playlist card holds no playlist logic. Trusted domains, import and export formats and the
  media access registry all live in `SharedPlaylistManager` and `MediaAccessRegistry`, and stay
  there.
- Export refuses an empty playlist with a notice; URLs typed by the local user are trusted by
  consent; peer pushed URLs go through the trusted domains check.

## What is wrong

**37% of the screen.** `Modifier.fillMaxWidth(0.37f)` is the entire width strategy for the
landscape panels (the portrait branch is dead code, see ROOM_SHELL). On a landscape phone that
is roughly 296dp, which is why in-room settings had to shrink its type to 13sp and 9sp, and why
the shared playlist truncates most file names. 37% is not a size, it is a guess.

**`CardSharedPlaylist.kt` is 567 lines and imports fourteen Material components**, four of
them dead. Its action row sits **below** the list, not above it: add, shuffle, clear all and an
overflow holding import and export. It uses 11sp rows, 10sp in its URL popup, and the Helvetica
face, the one file in the app that does.

**Three panels, three internal layouts.** The roster stacks two or three lines per person
(name, file, and a properties line with duration and size), sized off a 10sp base constant
with hand derived multipliers. The playlist is a list with a button bar and menus. Settings is
a grid of category cards, then a list. Nothing is shared, so they do not feel like three views
of one panel.

**In-room settings inject the engine category at a magic index** (`size - 2` of a list that
happens to be four long).

## The design

### Width is a size, not a fraction

Panels get a real width: `clamp(320dp, 38% of width, 420dp)` in the wide arrangement, full
width as a sheet in the tall one. The lower bound is what makes in-room settings readable at the
same type size as global settings, which is the whole point of deleting `settingROOMstyle`.
This is the one number for the panel; [PREF_SYSTEM](../PREF_SYSTEM/README.md) defers to it.

### One panel chrome

Every panel gets the same frame, `PanelFrame`, defined once:

- Header: 42dp, title in `label` type left, panel actions as glyph buttons right, hairline
  beneath.
- Body: scrolls, `gutter` horizontal padding, hairline separated groups, the desktop scrollbar.
- No inner cards. A panel inside a panel is the thing that made these feel cluttered.

The three panels then differ only in their body, which is the correct amount of difference.

### People is a roster

Each person is a 42dp row: readiness as a 6dp square in the left gutter (filled `ok` when
ready, hollow `bad` when not, so the difference is shape as well as colour), name in `label`,
and their file state in the `value` column as `same file`, `different` or `no file`.
Controllers take a 2dp `accent` left edge. The current user's row is the only one with a
filled ground. Tapping a row expands a second `note` line with the file name, duration and
size, which is where today's properties line goes.

That puts the single most useful fact in a room, whether everyone is on the same file, into an
aligned column you can scan, instead of an icon set you have to decode.

### The playlist is a list with header actions

The action row collapses into the panel header: add and overflow. Add opens the media intake
sheet, see [MEDIA_INTAKE](../MEDIA_INTAKE/README.md), whose link route replaces the playlist's
own URL popup. Shuffle, clear all, import and export sit in the overflow, which is a small
`panel` list, not a menu. The list then takes the full panel height instead of 75% of it.

Each entry is a 42dp row: index in `value` type in the left gutter, file name in `label` with
middle ellipsis so the extension survives truncation, and a state glyph on the right (playing,
available locally, missing). The currently playing entry takes the `accent` left edge. Tap plays
the entry; a trailing glyph opens a two row list, Play and Remove.

Middle ellipsis matters here: `The.Very.Long.Movie.Name.2019.1080p.BluRay.x264.mkv` truncated at
the end is unidentifiable, truncated in the middle is readable.

### In-room settings

Covered fully in [PREF_SYSTEM](../PREF_SYSTEM/README.md). From this folder's point of view the
change is that it stops being a grid inside a card and becomes the same console list as global
settings, at the same type size, inside the standard panel chrome, with the engine category
placed by name.

## Invariants

- Slide distance is read from the window size per composition, so it survives a resize.
- Panels sit above the transport and the play key (`zIndex(10f)`).
- The control panel shares the panels' column and is opened from the transport bar; it moves
  to the panel frame too.
- Export names the file `SharedPlaylist_<time>.txt`; import accepts `txt` and `m3u`.
- The playlist's paste action reads the clipboard through the app's own `ClipEntry.getText()`
  helper, which replaces the deprecated clipboard manager.

## Phases

1. Panel width and `PanelFrame`. All three panels move into it unchanged.
2. People rebuilt as the roster with the aligned file state column and the expanding row.
3. `CardSharedPlaylist.kt` split: the list, the header actions, and the entry row into separate
   files; then the Material components replaced. Pure move first, no behaviour edits.
4. Playlist row with middle ellipsis and state glyphs; the URL popup retired for the intake
   route.

## Risks

- Widening panels to at least 320dp covers more video in landscape. That is the correct trade
  for readable panels, but it should be checked on a 16:9 phone where the video is already
  letterboxed.
- The shared playlist's file pickers must keep launching after their list has dismissed, and
  the shuffle flag must keep being set first. Both rules move verbatim.
