# Desktop and keyboard

Assumes [FOUNDATION](../FOUNDATION/README.md) and [TV_AND_DPAD](../TV_AND_DPAD/README.md).

Files: `desktopApp/src/main/kotlin/app/desktop/Main.kt` (120),
`desktopApp/src/main/kotlin/app/desktop/DesktopPlatformCallback.kt` (50),
`room/ui/bottombar/RoomSeekbar.kt` (the seekbar key handler),
`home/components/HomeEngineWheel.kt` (the wheel key handler), `uicomponents/TvFocus.kt`.

## What it is

The `jvm("desktop")` target: one Compose for Desktop window, the KitePlayer engine, a mouse and
a keyboard. It shares every screen with the phone build, which is the point, and also the
problem: the phone build was designed for a thumb.

## What is right already

- Window-level keys run **after** focus dispatch (`onKeyEvent`, not `onPreviewKeyEvent`), so a
  space typed into the chat field never toggles playback. Only keys nobody consumed reach the
  player. That rule stays.
- The command line join (`--user --room [--host --port --pw --media --autoplay]`) is the desktop
  version of a launcher shortcut and stays as it is.
- The server host screen already makes its addresses selectable text.

## What is wrong

**Three keys exist.** Space toggles, Left and Right seek. No fullscreen key, no volume, no way to
reach the chat, no Escape anywhere in the tree, and the navigation host's own back handler is
empty, so no key leaves a page either. Every other desktop player answers all of these.

**There is no fullscreen.** `EnterRoomMode` is a no-op on desktop, so the room plays inside a
titled window with the operating system's chrome around it. Watching a film in a window with a
title bar is the one thing a desktop video app must not do.

**The window forgets everything.** It opens at 1280 x 800 on every launch, has no minimum size,
and does not remember where it was.

**A mouse gets touch feedback.** No hover state on rows, no cursor change on actions, no
tooltips. [HOME](../HOME/README.md) deletes the Material tooltips, which is right on a phone, but
on desktop a glyph-only button with no name is a guess.

**Only the settings list has a scrollbar.** Every other list scrolls with the wheel and gives no
sign that it can.

**Nothing can be dropped on the window,** and chat text cannot be selected or copied.

## The design

### Keys

All window-level, all after focus dispatch. A key does nothing while a text field has focus,
except Escape, which first clears that focus.

| Key | Does |
|---|---|
| Space | play or pause |
| Left, Right | seek back or forward by the jump amount |
| Shift + Left, Right | seek by five times the jump amount |
| Up, Down | volume by 5 percent |
| M | mute and unmute |
| F, or a double click on the video | fullscreen on and off |
| Escape | leave fullscreen; else close the open modal or panel; else hide the HUD |
| C | focus the chat composer |
| R | toggle ready |
| Ctrl or Cmd + , | open settings |

The same map drives [TV_AND_DPAD](../TV_AND_DPAD/README.md): a remote's centre button is Space,
its arrows are the arrows. One key map, two input devices.

### Fullscreen

`F` and a double click on the picture toggle `WindowPlacement.Fullscreen`. Entering a room does
not go fullscreen on its own; leaving the room always returns to the floating placement. In
fullscreen the mouse cursor hides together with the HUD and comes back on any movement.

### Pointer

- **Hover** lifts a row's ground by `ink` at 6 percent. Focus lifts by 12 percent and adds the
  border, so hover and focus never look the same.
- **Cursor** becomes a hand over anything clickable that is not a row, and a text cursor over a
  field.
- **Tooltips**, desktop only: a glyph-only button shows its name after 600 ms of hover, in `note`
  type on the `chrome` tier, under the glyph. Touch has no hover so it never sees one; TV has
  focus, and the focused control's name is spoken instead.

### Window

- Minimum 800 x 480: a 320dp side dock next to a 16:9 picture that is 480dp tall.
- Size, position and placement are saved in the desktop DataStore on change and restored on
  launch, clamped to the display that exists now.

### Scrollbars

Every scrolling list on desktop gets the unstyled scrollbar the settings list already has: 4dp
wide, `inkFaint`, hidden while idle. The screen frame and the panel frame apply it, so no surface
adds one by hand and none forgets to.

### Drop and selection

- A media file or a URL dropped on the room goes into the matching route of the
  [MEDIA_INTAKE](../MEDIA_INTAKE/README.md) sheet, as if that route had been chosen.
- Chat messages sit in a `SelectionContainer` on desktop, so text can be copied.

## Phases

1. The key map and fullscreen. Playback control is the missing basic.
2. Window memory and the minimum size.
3. Hover, cursor, tooltips and scrollbars, applied through the frames.
4. Drop and selection.

## Verification

The render harness cannot press keys. Desktop gets a manual checklist run before a release:
every key in the table, in and out of a text field, in and out of fullscreen, and the window
restored after a relaunch.

## Risks

- The key map must keep running after focus dispatch. A window-level `onPreviewKeyEvent` would
  steal the space bar from the chat.
- Fullscreen on macOS moves the window into its own Space. Leaving the room while fullscreen
  has to restore the floating placement, or the home screen inherits fullscreen.
- Hover must key off the pointer type, not the platform. Android reports hover for a mouse or a
  stylus, and a hover lift under a finger would look like a stuck press.
