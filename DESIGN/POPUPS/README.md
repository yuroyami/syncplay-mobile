# Popups and modals overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md) and
[GLASS_SURFACES](../GLASS_SURFACES/README.md).

Files: `uicomponents/SyncplayPopup.kt` (93), `uicomponents/MultiChoiceDialog.kt` (111),
`uicomponents/GlassComponents.kt` (150), `uicomponents/PopupMediaDirs.kt` (295),
`uicomponents/ScreenDimensions.kt` (34), `home/components/PopupAPropos.kt` (267),
`home/components/PopupDidYaKnow.kt` (88), `room/ui/tabs/PopupManagedRoom.kt` (163),
`room/ui/bottombar/PopupSeekToPosition.kt` (265), `preferences/settings/PopupColorPicker.kt`
(144), `preferences/settings/PopupTrustedDomains.kt` (134),
`preferences/settings/PopupChatColors.kt` (52), `theme/ThemePicker.kt` (the picker dialog),
and the URL entry popups inside `room/ui/bottombar/RoomMediaAddButton.kt` and
`room/ui/rightcards/CardSharedPlaylist.kt`.

## What it is

About fifteen modal moments: eight callers of `SyncplayPopup`, the multi choice dialog, the
theme picker's `BasicAlertDialog`, four users of the glass alert dialog, two glass bottom
sheets, and seven files with a glass dropdown menu. They are the app's most duplicated pattern.

## What is right already

- Every popup is a real `Dialog` window with `usePlatformDefaultWidth = false` and outside
  dismissal re-implemented as a scrim click, with a matching click consumer on the content. The
  Android window blur flag can only be set from inside that window, which is why
  `DialogBackdropBlur()` is emitted as the first child of the dialog (or, for the alert dialog,
  inside its confirm slot, the one slot that always exists).
- `imePadding` sits on the scrim box, not the content, so the centring container moves.
- One glass-aware scrim already exists, `glassScrim`: black at 28 percent with glass on and 55
  percent with it off.
- The alert dialog is used through one wrapper everywhere; the four `AlertDialog` imports
  outside it are dead imports.

## What is wrong

**There is no popup system, only a popup convention.** `SyncplayPopup` frames eight popups;
the multi choice dialog, the theme picker and the four alert dialog users each frame their
own, with their own header, button row and padding. The seek-to popup shadows its viewmodel
inside the dialog window because the dialog has its own composition.

**Menus are a crash risk and a form language problem.** `ExposedDropdownMenu` has an unguarded
`coerceIn` in Material's position provider that aborts on landscape and rotation, which is why
its two callers pass a `dropdownMenuMaxHeight` measured against the shorter window side. The
seven plain dropdown callers do not crash but they are menus: the tab overflow, the playlist
actions, the media add button, the GIF tile actions, the theme creator's picker, and two in the
control panel.

**Buttons are Material `Button` and `TextButton`,** so the most emphasised moment in any flow
(confirming something) is stock Material. The media directories popup alone carries a `Card`,
three `Button`s, two `TextButton`s and a ripple.

**Back handling is Android only.** `dismissOnBackPress` is the whole story. Desktop has no
Escape, iOS has no gesture, and the navigation host's `onBack` is empty, so nothing outside a
dialog answers a back press at all.

## The design

### One frame, three sizes

A single `Modal` composable owning the dialog window, scrim, entry, focus, back handling and
dismissal. Callers supply a title, a body and actions. Three sizes only:

| Size | Width | Used for |
|---|---|---|
| `ask` | min(320dp, 88%) | yes or no, one short question |
| `panel` | min(440dp, 92%) | pickers, editors, lists |
| `full` | 92% x 88% | about, media directories, anything with real content |

On compact widths `panel` and `full` become bottom sheets rather than centred dialogs, because a
centred dialog on a phone wastes the edges and puts actions far from the thumb. A sheet is still
a dialog window with bottom aligned content that slides up over `move`, so the window blur and
the scrim keep working.

### Frame anatomy

- Header 42dp: title in `label`, close glyph right with a 48dp target, hairline beneath.
- Body: `gutter` padding, scrolls when needed, with the hairline appearing under the header only
  once the body has scrolled.
- Actions: a 54dp row, hairline above, actions right aligned. The confirming action is the only
  one with a fill, and only `ask` size gets the brand gradient. Destructive actions are `bad`
  text with no fill.
- Dismissal: scrim tap, the close glyph, Android back, desktop Escape. Whether scrim tap and
  back are allowed is one flag, as today. iOS has no system back and relies on the glyph and
  the scrim.
- Focus: the dialog window traps focus on every platform. Inside it, the first action gets
  initial focus in keyboard input mode, so a remote can answer a question without hunting.

### Menus become lists

Every `DropdownMenu` and `ExposedDropdownMenu` is deleted. Choosing from a set is either:

- a stepper in place, when there are under five options
  (see [PREF_SYSTEM](../PREF_SYSTEM/README.md)),
- a `panel` modal with a hairline list, when there are five or more, or
- a cell in the room's rail or a row in a panel, when the "menu" was really a set of actions
  (the tab overflow, the playlist actions, the GIF tile actions).

The `dropdownMenuMaxHeight` workaround and the glass menu wrappers are deleted with them;
`ScreenDimensions.kt` keeps only the width classes that
[NAVIGATION](../NAVIGATION/README.md) puts there.

### Popup by popup

| Popup | Becomes |
|---|---|
| `MultiChoiceDialog` | the `panel` list, and the only chooser in the app |
| `PopupSeekToPosition` | `panel` with a drawn HH:MM:SS field: three tabular cells, focus chained HH to MM to SS as today, minutes and seconds clamped at 59, plus the custom skip action |
| `PopupColorPicker` | `panel`, picker plus the swatch grid, hex in a `value` field; its reset action closes the modal, which it does not today |
| `PopupTrustedDomains` | `panel` list with an inline add row, each domain a hairline row; keeps its split-on-read, join-on-save normalisation; all six of its strings move into resources |
| `PopupChatColors` | folds into `PopupColorPicker`; it is a colour list with a different title |
| `PopupManagedRoom` | `ask` size with two clear paths, create or identify |
| `PopupMediaDirs` | `full`, a directory list with per row remove and a clear-all confirmation; keeps its two-phase removal |
| `PopupAPropos` | `full`, restyled as an about sheet: wordmark, version, links, and the Watch alone action, which [HOME](../HOME/README.md) also puts on the home screen |
| `PopupDidYaKnow` | `panel`, one tip at a time with a dot indicator and a Next action; today the index never advances, so only one random tip ever shows |
| The theme picker | `panel` list, see [THEMING](../THEMING/README.md) |
| The two URL entry popups | the link route of the [MEDIA_INTAKE](../MEDIA_INTAKE/README.md) sheet |
| The four alert dialogs | `ask` |
| The two bottom sheets | the room panel from [TRANSPORT](../TRANSPORT/README.md) |

## Phases

1. The `Modal` frame with three sizes, back and Escape handling, and the FOUNDATION button
   treatments.
2. Migrate the four settings popups, since they are the most contained.
3. Replace every dropdown with a stepper, a `panel` list, or a rail cell. Delete the
   `dropdownMenuMaxHeight` workaround and the glass menu wrappers.
4. Migrate the remaining popups. Delete `SyncplayPopup`, `MultiChoiceDialog`, the glass alert
   dialog and sheet wrappers, and the last `AlertDialog`, `BasicAlertDialog` and
   `ModalBottomSheet` usages.

## Risks

- Focus trapping and back handling differ per platform, and Material's dialogs were doing that
  work. The `Modal` frame must handle Android back, desktop Escape and TV D-pad focus
  explicitly, and the dialog window is what keeps focus inside.
- `PopupMediaDirs` and `PopupTrustedDomains` carry real logic (bookmark registry, domain
  matching, normalisation). They are reframed only; the logic moves untouched.
- Landscape with the keyboard up leaves very little height. A sheet body scrolls; a centred
  `panel` shrinks to the space left, and the seek-to editor requests focus only after the
  modal has finished entering, so the keyboard does not fight the entry animation.
