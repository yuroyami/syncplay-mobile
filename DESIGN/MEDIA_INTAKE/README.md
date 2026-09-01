# Media intake overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md) and [POPUPS](../POPUPS/README.md).

Files: `room/ui/bottombar/RoomMediaAddButton.kt` (365),
`uicomponents/PopupMediaDirs.kt` (295), `room/sharedplaylist/MediaAccessRegistry.kt` (202),
`player/resolver/MediaResolver.kt` (82), the injection path in `player/PlayerImpl.kt`, and the
Android custom chooser in `SyncplayActivity.kt`.

## What it is

Getting a file into the room: the storage picker, an Android specific chooser for SMB and cloud
providers, a URL entry path, the playlist import that lives in the playlist panel, and the
remembered directories that let a shared playlist re-open files by name.

## What is right already

- The picker is launched only after its menu has finished dismissing (the FileKit iOS rule),
  in two places: the media picker here and the subtitle picker in the control panel.
- Exactly one iOS security scope is held at a time; opening the same file twice does not
  double-grant, and switching to a URL releases it.
- Media injection is serialised, and the media is installed before the engine loads it,
  because engine load events read the current media.
- The resolver runs only when its preference is on and the URL does not already look like
  direct media; a resolved title and duration replace the file name and duration while the
  playlist keeps the original URL.
- `MediaAccessRegistry` resolves a name from a direct bookmark first, then re-indexes every
  remembered directory and retries, so a moved file heals itself.
- Removing a remembered directory updates the preference and the registry together.
- The URL field already has a help line, a link glyph and a paste action.
- The add menu opens on its own the first time a room is entered.

## What is wrong

**One button hides three different flows.** Local file, the Android chooser, and URL live
behind the same plus, and which ones exist depends on the platform, while playlist import lives
somewhere else entirely. So the button means something different on Android and iOS, and
neither is explained.

**URL entry does not say what it accepts.** The app can resolve YouTube, SoundCloud, PeerTube,
Bandcamp and MediaCCC on Android, and YouTube on iOS, but validation is "not blank". When a link
fails, nothing distinguishes "not supported here" from "that link is broken": a failed resolve
silently falls back to the raw URL and then fails with the generic loading error. With the
resolver switched off, nothing says so.

**The Android chooser hands out URIs that die with the process.** It uses `ACTION_GET_CONTENT`,
whose grants are not persistable, so a file picked from a cloud provider cannot be remembered
and breaks after a restart.

**Remembered folders exist as a setting but look like an error.** The media directories row is
in General settings and in the playlist panel, which is right, but the editor is a bare list
with no empty state, a name that falls back to "Undefined", no sign that a folder has lost its
grant, and no count of what it holds.

## The design

### The plus opens one sheet with named routes

A `panel` modal listing what is actually possible, each as a 54dp row with a glyph, a name in
`label` and one `note` line. The glyphs differ per route, which is why the rows keep them:

| Route | Note line |
|---|---|
| From this device | pick a video or audio file |
| From a link | YouTube and a few others, resolved on device |
| From a network share | SMB, cloud and other providers (Android only) |
| Import a playlist | m3u and txt |

Routes absent on the platform are not shown. That makes the button mean the same thing
everywhere: it opens the list of ways in. The sheet is what opens on first room entry, and the
playlist panel's add action opens the same sheet.

### The link route explains itself

The field is the hairline field with a `note` line naming the supported sites for this platform.
Paste triggers immediate recognition using the helpers that exist and are not surfaced today
(`urlLooksLikeDirectMedia`, `extractYoutubeId`, the resolver's own support check):

- a resolvable link shows the title and duration before you confirm,
- a direct media URL says it will be played as is,
- an unsupported link says so, and says whether the resolver is switched off.

A resolve that fails after recognition says that it failed, instead of falling back silently.

### Remembered folders become a real editor

The directory list stays a settings row (Media folders) with the total number of indexed files
in the value column, since the registry stores bookmarks by file name and not per folder, and
keeps its emergency appearance when a file cannot be found. Same editor, two ways in.

Each row shows the folder name, its path in `note`, and whether it can still be opened. A row
that has lost access is marked `warn` with a Re-grant action, which is the state that actually
matters. An empty list says "No folders yet" with one Add action.

### The Android chooser persists its grants

The custom chooser moves to `ACTION_OPEN_DOCUMENT` and takes a persistable read permission, so
a file from a cloud provider can be registered and re-opened like any other.

## Invariants

- Picker launch happens after the sheet has dismissed, never from inside it, on both picker
  sites. The 50 ms deferred dismissal stays.
- The add button claims the room's initial D-pad focus only while no video is loaded.
- Direct media extensions skip the resolver; the resolver preference off skips it entirely.
- The registry's self-heal order is direct bookmark, then re-index and retry.

## Phases

1. The route sheet replacing the current button behaviour, routes gated per platform, playlist
   import inside it.
2. Link route with recognition and the supported sites line; the explicit failure state.
3. Media folders editor: empty state, lost access state, the count, the name fallback.
4. The Android chooser on persistable grants.

## Risks

- The FileKit iOS workaround requires the picker to be launched after the sheet is dismissed,
  not from inside it. The route sheet must dismiss first and then launch, exactly as the current
  code does, or file picking breaks on iOS with no error.
- Security scoped bookmarks on iOS and persistable SAF URIs on Android are what make remembered
  folders work. Surfacing them in settings must not change when they are acquired or released.
- Changing the Android chooser's intent changes which providers appear in it. Check SMB and at
  least one cloud provider still show up before shipping phase 4.
