# Chat overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `room/ui/chat/RoomSectionChat.kt` (353), `room/ui/chat/RoomFadingChatLayout.kt` (113),
`room/ui/chat/GifPanel.kt` (412), `room/models/Message.kt` (123),
`room/models/MessagePalette.kt` (12), `uicomponents/MessagePalette.kt` (37), `klipy/KlipyUtils.kt`.

## What it is

The room's conversation. It has two states: a full list when the HUD is up, and transient
messages fading over the video when the HUD is hidden. It also hosts the Klipy GIF and sticker
search.

## What is right already

The inset and interop handling here was hard won and does not change:

- The cutout inset and 8dp margins are applied to each of the three section children, not to
  the column, and each child's tap shield `pointerInput` comes **before** its inset padding, so
  the notch strip belongs to the row and a fat finger there does not fall through to the HUD
  dismiss underneath.
- `ChatTextField`'s inner field must not reuse the outer modifier, or the shield and insets
  double apply.
- The message cap is the server's own `maxChatMessageLength`, floored at 1 so a hostile zero
  cannot eat every message. Backslashes are never stripped; the JSON layer escapes them.
- Sender and content are wrapped in first-strong isolates separately, so a right to left name
  and a left to right message do not scramble. System and error lines are deliberately not
  isolated.
- Messages are marked seen only while the HUD is visible, because the HUD is composed at alpha
  0 when hidden and unconditional marking would kill the fading layout. `seen` is a plain
  field, not Compose state, on purpose.
- GIF tiles need `fillMaxWidth` on both the tile and the image, and alpha is passed as a
  parameter, because an empty `UIImageView` reports zero size and Compose never re-measures
  UIKit interop after the image loads.
- Klipy requests keep `expectSuccess = true`, or a Cloudflare refusal decodes into an empty
  success and the grid says "no results" with nothing logged.

Any redesign keeps every one of those.

## What is wrong

**The composer is an `OutlinedTextField` with three Material `IconButton`s** (GIF, send, clear)
sitting on glass, its box outline fighting the glass rim behind it. The brand gradient is
painted on the field label, on all three glyphs, and on every character typed, at once.

**Messages are one stream at 9sp.** The chat font size is a preference whose default is 9 and
whose range starts at 6. Six semantic colours exist (the `COLOR_*` prefs behind
`MessagePalette`) but they are all applied as text colour on the same row shape, so a system
event, an error and a person talking are the same object in three tints. Over video at 9sp,
tint alone does not carry the difference.

**Timestamps are inline.** They take horizontal room from the message on the narrowest surface
in the app, on every line, whenever the timestamp switch is on.

**The fading layout is a third rendering.** It draws its own message at 13sp (8sp in picture in
picture), ignores the font size, outline and shadow preferences, shows only the last message,
never the user's own, and its hold time is the fading duration preference while its fade is a
hand written keyframe.

**The GIF panel is five Material `FilterChip`s over an adaptive grid** with 80dp tall
non-square tiles, and a failed request looks exactly like an empty result.

**`MSG_MAXCOUNT` is read by nothing.** A shipped preference with a title and a slider that does
nothing.

## The design

### Two message shapes, not six tints

A room's chat has exactly two kinds of line, and they should not look alike:

| Kind | Form |
|---|---|
| Person | name in `value` type in the person's tag colour, message in `note` type on the next line at `ink`. No bubble, no background |
| Event | a single `note` line at `inkDim`, prefixed by a 2dp `accent` stub in the gutter. Errors take `bad` for the stub and the text |

An image or GIF message is the person shape with the image where the text would be, at the
same indent.

Removing bubbles is deliberate. Over video, a bubble is another opaque rectangle competing with
the picture; a hanging indent with a coloured name does the same job with no fill. The
background opacity preference already defaults to zero, so most users see no bubble today.

### The chat size preference gets a floor

`MSG_FONTSIZE` becomes 11 to 24 with a default of 13, and a stored value under 11 is read as 11.
The six colour preferences keep their meaning: the name takes the tag colour, the event line
takes the system or error colour, the message takes the user message colour.

### Timestamps sit in the gutter, on demand

When the timestamp switch is on, the time moves to a right hand `value` column in `inkFaint`,
and appears only when the message is more than a minute after the one above it. Grouped
messages from the same person within a minute drop the repeated name too, so a back and forth
reads as a conversation rather than a log. Every row keeps its full spoken description, name and
time included, whether or not they are drawn.

### The composer is a bar, not a box

A single hairline rule above the input, the field with no border at all, and a send glyph that
is `inkFaint` until there is text and `accent` after. The GIF glyph sits on the left. The whole
composer is 42dp plus insets, with 48dp targets on both glyphs. No gradient anywhere on it.

The keyboard's send action keeps its switch (`MSG_BOX_ACTION`) and stays suppressed while the
GIF panel is open, because the text is then a search query. Emoji stay out of any styled span.

### The GIF panel is a drawer of the composer

It opens above the composer, full panel width. The five tabs (GIFs, stickers, trending, recents,
favourites) become a row of Tags with one selected, since a Segmented control stops at four
cells. The grid stays adaptive but its tiles become square with 4dp gaps and `radiusTight`.
Search is the same hairline field, pinned at the top of the drawer, with its 400 ms debounce on
typing only; tab changes stay instant. Selecting sends immediately and closes.

A failed request is distinguished from an empty one: the fetchers return a result with an error
flag instead of an empty page, and the drawer shows one `note` line with a Retry action.

### Fading chat is the same component

`RoomFadingChatLayout` reuses the two shapes at the same type size, with only the container
changing: no background, the fading duration preference as the hold, a `move` fade out. It
shows up to `MSG_MAXCOUNT` recent unseen lines (the pref finally does something; its default
becomes 3), still skips the user's own messages, and in picture in picture drops to the 11sp
floor rather than 8sp. The outline preference applies here too, which is where it matters most:
this is the one text in the app drawn over video with no panel behind it.

## Invariants

Beyond the list under "what is right already":

- Image detection strips the query and fragment before testing the extension, and requires a
  sender, so a system line can never render as an image.
- Focus is force-cleared when the HUD hides, or the input stays focused behind an invisible
  overlay with the keyboard up.
- The list opens at its last item through the initial scroll index and animates only on growth,
  so re-entering the room does not replay the backlog.
- Favourites bypass the network and filter by slug; pages de-duplicate by id because Klipy
  repeats items across page boundaries; share tracking fires on both send paths, fire and
  forget, and swallows everything except cancellation.
- A whitespace-only send still clears the draft and the GIF panel today. It should not clear
  anything; that is a small fix folded into phase 2.

## Phases

1. The two message shapes and the grouping rule, in both the list and the fading layout, with
   the size floor and `MSG_MAXCOUNT` wired.
2. Composer rebuilt on the hairline field. Delete `OutlinedTextField` and the three
   `IconButton`s, and the gradient on the field.
3. Timestamp gutter and the one minute rule.
4. GIF drawer attached to the composer: tags, square tiles, the error state.

## Risks

- Message grouping changes what a screen reader announces. Each grouped row keeps its own
  content description including the name and time, even when they are not drawn.
- Removing bubbles reduces contrast against bright video. The fading layout is the risky one
  since it has no panel behind it; the outline preference is what carries it, so wiring it
  there is part of phase 1, not a follow up.
- The chat size floor changes a stored value for anyone who set it below 11. Reading it as 11
  is the whole migration; nothing is rewritten in storage.
