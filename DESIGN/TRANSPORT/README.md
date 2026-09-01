# Transport overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `room/ui/bottombar/RoomSeekbar.kt` (272), `room/ui/bottombar/RoomControlPanel.kt` (1011),
`room/ui/bottombar/RoomSectionBottomBar.kt` (60),
`room/ui/bottombar/RoomControlsAboveSeekbar.kt` (47),
`room/ui/bottombar/RoomReadyButton.kt` (85), `room/ui/bottombar/PopupSeekToPosition.kt` (265),
`room/ui/bottombar/BlackContrastUnderlay.kt` (28), `room/ui/misc/RoomPlayButton.kt` (57),
plus the seek paths in `protocol/event/RoomEventDispatcher.kt` and `player/PlayerImpl.kt`.

## What it is

The seekbar, the play button, the ready button, fast forward and rewind, and the control panel
that holds aspect ratio, seek-to, undo-seek, the gesture switches, chapters, and the audio and
subtitle sheets. This is the app's signature surface and the thing people touch most.

## What is right already

- The seekbar previews while dragging and seeks the engine **once, on release**. Per frame seeks
  are expensive on mpv and VLC. `pendingSeekFromMs` is captured on the first drag event, so it
  holds the position before the drag, not the position at release.
- The track is already custom: a thickness that grows while dragging, a gradient fill, chapter
  dots with 20dp targets around 8dp visuals, and inline timecodes that hide while the bubble is
  up. Only the drag and value semantics still come from the Material `Slider`.
- `analyzeChapters` has exactly one caller, the seekbar, keyed on the file name. Every engine's
  implementation clears the chapter list first, so a second caller would blank the dots
  mid-frame. The chapter dropdown deliberately reads the same list without re-analysing.
- D-pad left and right on a focused seekbar become real jump seeks, not slider steps, and key
  up is ignored or the seek fires twice.
- Every play, pause and seek is guarded on `media != null`, because VLCKit 4 crashes on a
  null media, and `noteExpectedPlaybackState` runs before the engine is touched because
  ExoPlayer reports the change synchronously inside `play()`.

## What is wrong

**The seekbar's behaviour is a Material `Slider`.** Its hit box is 56dp, its thumb is a custom
8 x 30dp box, and its drag semantics, value semantics and touch behaviour are Material's. The
app's single most identifying control is half drawn and half adopted.

**There are three seek orders.** The slider sets the pending origin, sends the announcement,
then seeks. Undo seeks first and announces second. Chapter jumps set the origin only when not
in solo mode. And the slider is the one seek path that records nothing for undo in solo mode,
so a scrub cannot be undone while the jump buttons can. One contract, written four times, each
slightly differently.

**`RoomControlPanel.kt` is 1011 lines** and does seven jobs: aspect ratio, seek-to, undo-seek
with its confirmation, the two gesture switches, the track sheet, the subtitle search sheet,
and the chapter dropdown, plus its own dropdown button and scrollbar helpers. It imports nine
Material components, three of which are dead imports (the live ones are the glass wrappers,
which render Material underneath). It carries 10, 11, 12 and 13sp text and three M3 type roles.

**Aspect ratio labels are engine strings.** ExoPlayer localises its five modes; mpv, KitePlayer
and every engine's "NO PLAYER FOUND" are English literals, and AVPlayer puts its raw
`AVLayerVideoGravity` constant on screen.

**The play key decides from one state and draws from another.** It renders from
`isNowPlaying` and decides what to do from a live `isPlaying()` probe, which VLCKit answers
late. The key can show pause and send play.

**Buffered progress does not exist.** No engine exposes a buffered position, so the track cannot
show what is loaded.

## The design

### One seek path

Every seek in the app goes through one call on the dispatcher:

```kotlin
fun seek(targetMs: Long, fromMs: Long = player.currentPositionMs())
```

which, in this order and nowhere else: records `fromMs` as the pending origin, sends the
announcement (a no-op in solo mode), seeks the engine, and in solo mode records the pair for
undo (online, the inbound echo records it, as today). The slider, the seek-to editor, the custom
skip, the jump buttons, undo, chapter jumps, the D-pad, the desktop keys and the gesture
double tap all call it. Undo's reversed order and the slider's missing solo undo disappear by
construction, and the phantom duplicate seek stays fixed because the pending origin is still
single use.

### The seekbar is drawn

Replace the Material `Slider` with a drawn transport track built on FOUNDATION's scrub track:

- Track 4dp in `trackOff`. Played portion filled with `brandField`. Buffered portion at 30
  percent `ink` between them, drawn only when the engine can answer.
- Playhead: a 3 x 18dp bar in `ink`, not a circle. It is a playhead, so it looks like one.
- Chapter marks: 1dp full height ticks in `rule`, `accent` for the chapter under the playhead.
  Marks in the first second are skipped, as the dots are today. `SHOW_CHAPTER_DOTS` and
  `CHAPTER_DOTS_CLICKABLE` keep their meaning; a mark's target stays 20dp around a 1dp visual.
- Scrub bubble: a `chrome` tier panel above the finger showing the target timecode in `value`
  type, tabular so digits do not jitter, plus the chapter name when there is one. Clamped inside
  the track as today.
- Visual 18dp tall, target 48dp, all of it in one `pointerInput` and `semantics(role = Slider)`
  with a spoken timecode.

Buffered progress needs one optional engine call, `bufferedPositionMs(): Long?`, default null:
ExoPlayer has `bufferedPosition`, AVPlayer has `loadedTimeRanges`, mpv has
`demuxer-cache-time`, KitePlayer answers from its demuxer, VLCKit answers null. Null draws no
band.

### Timecodes are one component

Elapsed and total sit in ad hoc `Text` at 11sp today. They become a single `Timecode`
composable in `value` type, tabular, with the total dimmed to `inkDim` (not `inkFaint`: over
video the total must still clear the underlay). It is used by the seekbar, the scrub bubble,
the seek-to editor and the status line. Unknown duration renders as `--:--`, which retires the
`"???"` branch that nothing reaches.

### The control panel splits

`RoomControlPanel.kt` becomes five files, none over 250 lines:

| File | Job |
|---|---|
| `TransportBar` | the bar itself: play, ready, seekbar, jump buttons, add media, panel entry. Absorbs `RoomSectionBottomBar` and `RoomControlsAboveSeekbar` |
| `PanelSeek` | seek-to editor, custom skip, undo with its confirmation, the two gesture rockers |
| `PanelTracks` | audio and subtitle selection as hairline lists, the external subtitle picker |
| `PanelSubtitleSearch` | the OpenSubtitles sheet: query, language, results, quota state |
| `PanelAspect` | aspect ratio cycling and its localised label |

Chapters move out of the panel entirely and onto the seekbar, where the marks already are: tap
a mark to jump, long press the track to open a chapter list panel. One concept, one place. The
dropdown button and action helpers die with the menus.

The gesture switches stay in the panel, as rockers, because they were moved there so they can
be flipped mid-playback.

### Controls are drawn, not Material

- `Switch` inside the panel becomes the rocker.
- The track dropdowns become hairline lists inside the panel sheet, since a track list is a
  list, not a menu. Selection still closes the sheet before the engine call runs.
- `ModalBottomSheet` becomes the FOUNDATION panel: glass, `radiusPanel` on the top corners
  only, hairline rim, a Handle to drag. The sheet keeps `imePadding` and a maximum height so
  the query field is not crushed in landscape.
- `OutlinedTextField` in the subtitle search becomes the hairline field.
- `CircularProgressIndicator` becomes the Progress bar along the panel's top rim.
- `AlertDialog` for undo goes to the shared modal, `ask` size.
- The panel's opacity preference (`ROOM_UI_OPACITY`, clamped to 55 percent) maps onto the
  panel tier's tint, so the pref keeps its meaning.

### Play, ready, and the jump buttons

- Play is the room's gradient moment: a 60dp square key with `radiusPanel`, filled
  `brandField`, glyph in `ground`. It acts on the state it shows. It is the only filled control
  in the room.
- Ready is a Tag: hairline at rest, filled `ok` with the label in `ground` when ready, label in
  `value` type so it reads as state. Hidden in solo mode, as today.
- Fast forward and rewind become glyph plus a `value` number ("10 s"), so the jump amount is
  visible without opening settings. A long press opens the jump editor directly; there is no
  long press today.
- The add media button and the panel entry keep their current targets and move onto glyph
  buttons.

### Contrast under the bar

`BlackContrastUnderlay` is a ten stop black wash over the bottom 30 percent of the room. Keep
the job, change the form: a `ground` to transparent vertical gradient over the bottom 96dp at
70 percent peak, drawn by the frame's bottom dock. A flat block reads as a letterbox bar; a
gradient reads as shading.

### Labels

Every aspect ratio label and every "no player" string moves into resources, one per engine
mode, and AVPlayer's modes get names. The OSD shows the resource, never the engine's constant.

## Invariants

Kept verbatim, because each one was a bug:

- Seek on release only; preview while dragging; pending origin captured on the first drag
  event from the engine's live position.
- `isSliderInUse` means pressed or dragged, never focused, or D-pad focus freezes the track.
- The position flow does not write the track while it is in use.
- The pending origin is single use and negative means none, because zero is a real origin.
- Seeks under one second are suppressed and not recorded for undo.
- The announcement reports `expectedPlaying`, never a live `isPlaying()`.
- `controlPlayback` returns early in the background, so a lifecycle pause never reaches the
  room; a play refused by readiness marks the user ready instead.
- The track sheet does not open when there is no media, and closes before the selection runs.
- The subtitle picker launches after its menu has finished dismissing (the FileKit iOS rule).
- Subtitle quota errors render inside the sheet, because an OSD would be behind it.
- `TrackChoices` carries per-engine fields and survives media changes.

## Bugs fixed on the way

- Slider seeks are not undoable in solo mode.
- The play key can act against the state it shows.
- The subtitle picker accepts `.idx`, `.sub`, `.sbv` and others that the loader then rejects
  with a generic error; the picker filter and the loader whitelist become one list.
- AVPlayer's aspect ratio OSD shows a raw constant.
- `supportsScreenshot` gates a button that no longer exists; the flag goes, the API stays.
- The custom skip success notice passes a bare second count where the seek-to notice passes a
  formatted timecode, into the same string.

## Phases

1. The one seek path in the dispatcher, all callers moved onto it. Behaviour only, no visual
   change, and the phantom seek regression re-tested.
2. Drawn seekbar replacing the Material `Slider`, plus `Timecode` and the optional buffered
   position. Highest visibility, and the seek contract is now one function so it is testable.
3. Split `RoomControlPanel.kt` into the five files. Pure move, no logic edits.
4. Replace the Material components inside those files with FOUNDATION controls.
5. Move chapters onto the seekbar; delete the chapter dropdown.
6. Play key, ready tag, jump buttons, the contrast gradient, and the labels.

## Risks

- The seekbar is load bearing for sync. Phase 2 must not change what phase 1 established, and
  the phantom seek regression (duplicate "X jumped from A to B") is the specific thing to
  re-test after both.
- `RoomControlPanel.kt` at 1011 lines hides behaviour that is not obvious from reading. Phase
  3 is a pure move with no logic edits, so a diff can be checked line for line.
- Buffered progress is new engine API. An engine that cannot answer returns null and the track
  draws no band; it never blocks.
