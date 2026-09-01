# Preferences overhaul

Assumes [FOUNDATION](../FOUNDATION/README.md). Read that first.

Files: `preferences/Preferences.kt` (1175), `preferences/Pref.kt` (138),
`preferences/PrefExtraConfig.kt` (44), `preferences/SettingComposable.kt` (298),
`preferences/settings/MySettings.kt` (225), `preferences/settings/SettingsUI.kt` (282),
`preferences/settings/SettingCategory.kt` (20), `preferences/settings/SettingStyling.kt` (8),
`preferences/settings/PopupColorPicker.kt` (144), `preferences/settings/PopupTrustedDomains.kt`
(134), `preferences/settings/PopupChatColors.kt` (52), `room/ui/rightcards/CardRoomPrefs.kt`
(97), the settings drawer inside `home/components/HomeTopBar.kt`, and the four engine
`configurableSettings()` overrides (`KiteImpl`, `MpvImpl`, `ExoImpl`, `VlcKitImpl`).

## Measured, not guessed

A headless harness renders the real settings composables and reports their height. Everything
below is from that, not from an impression.

| Category | Prefs | Height at 360dp | Per pref |
|---|---:|---:|---:|
| Network (global) | 4 | 508dp | 127dp |
| Advanced (global) | 4 | 351dp | 88dp |
| Chat properties (in room) | 10 | 1008dp | 101dp |
| Player settings (in room) | 10 | 1189dp | 119dp |
| Player settings, in the room card at 300dp | 10 | 1239dp | 124dp |

A phone viewport is about 700dp. Player settings is 1.7 screens for ten switches and sliders. In
the room it lives in a panel that is 37% of a landscape screen, so it is closer to four screens
of scrolling inside a box the size of a playing card.

Evidence renders are in `evidence/`.

## What there is

97 preferences carry a `SettingConfig` (title, summary, icon, optional control): 95 declared in
`Preferences.kt` and 2 declared inline inside VLCKit's engine category. Ten static categories in
`MySettings.kt` (five global, five in-room) plus one category per engine, built at runtime:
KitePlayer 13 prefs, mpv 8, ExoPlayer 3, VLCKit 3, AVPlayer none.

Six chat colours live outside any category and render through their own popup. Ten more prefs
have a full config and sit in no category at all: five `OSD_*` switches that the room reads but
nobody can change, `DOUBLETAP_SEEK` and `SWIPE_GESTURES` (moved to the control panel on
purpose), and three that nothing reads: `MSG_MAXCOUNT`, `AUDIO_DELAY`, `SUBTITLE_DELAY`.

## What is wrong

**1. Every summary is printed in full, on every row, always.**
93 distinct summary strings. 34 are over 110 characters, 72 are over 60, the longest is 266.
The renderer sets no `maxLines` and no ellipsis, so one preference can be a seven line
paragraph. This is roughly 70% of the vertical cost.

The deeper issue is what the summaries are. They are documentation, not state. A help article
printed beside a switch. The row should say what the setting is set to; the explanation belongs
where you are changing it.

**2. One row layout serves eight controls.**
`SettingComposable` is a single `Column` with `when` branches. The seams show:

- A text pref gives its field `weight(1f)` in the same row as a label column that also has
  `weight(1f)`. The row splits 50/50 and both halves are too narrow.
- A choice label is capped at 140dp with no `maxLines`, so "Netty (Recommended + TLS)" wraps to
  three lines and crushes the summary beside it into seven.
- A slider puts its track below the row and its value across the row on the right, far from the
  control it belongs to.
- The click target is the whole column, slider included.

**3. No width strategy.** The same layout runs from 300dp to 1400dp. Line measure goes from
about 25 characters to about 110. Comfortable is 45 to 75. The global grid is a fixed two
columns at every window width.

**4. Settings have no home.** Global settings are a drawer that expands inside the home top
bar. In-room settings are a floating card at 37% width. Neither is a screen, so the layout can
never get the room it needs. The response was to shrink type: `settingROOMstyle` drops titles to
13sp and summaries to 11sp, and the room's category grid passes `titleSize = 9f`. A container
problem treated with typography.

**5. Nothing is findable.** No search. `INROOM_HAPTICS` is declared with eight prefs and never
added to `SETTINGS_ROOM`, so nobody can reach them. The five `OSD_*` switches have no row.
`AUDIO_LANG` and `CC_LANG` appear in both the global and the in-room lists, which is deliberate
but undocumented.

**6. Magic numbers fighting the theme.** `SettingStyling(titleSize, summarySize, iconSize,
paddingUsed)` applied with `.copy(fontSize = ...)` over the M3 type scale, in two variants that
differ only by shrinkage. `showSummariesByDefault` is declared and read by nothing.

**7. Navigation is a three state enum.** `SettingGridState` (declared in the home package,
consumed by the settings package and the room card) with no header telling you which category
you are in, a back control drawn as a forward arrow, and two unrelated card designs for the
same concept.

**8. It is Material 3.** Pill switches, fat slider thumbs, `AlertDialog`, `Card`, M3 type roles.
This is what makes it look like a generic settings screen instead of Synkplay's.

**9. Engines edit shared declarations at runtime.** `Pref.config` is built lazily once per
process and shared by every host. Each engine's `configurableSettings()` then writes
`config?.extraConfig = ...` (and, for mpv's interpolation switch, `config?.dependencyEnable`)
onto those shared objects, every time the room card is opened, and never reverts them. Eleven
integer prefs have no control at all until an engine has done this, and the two mpv string
prefs would render as inline text fields if shown before mpv injects their choice lists.

## The design

### Content model: name, value, explanation

Split what is now one `summary` into three roles.

- **Title**: what the setting is. Unchanged.
- **Value**: what it is set to. This is what the row's right hand column shows. Derived from the
  current value for most controls, so it costs nothing per preference.
- **Explanation**: the existing long summary, moved behind disclosure.

Where the explanation goes:

- Any row that opens an editor shows it in the editor, right above the control. That is the
  moment it is wanted.
- Toggles have no editor, so a long press expands it inline.
- A persisted **Show descriptions** switch turns explanations back on for every row, for people
  who liked them. This is what the dead `showSummariesByDefault` field should have been.

`SettingConfig` gains two optional fields. All 97 declarations keep working untouched.

```kotlin
data class SettingConfig(
    var title: StringResource = Res.string.okay,
    var summary: StringResource = Res.string.okay,
    // NEW: the one line "what is this set to". Null means derive it from the value.
    var stateSummary: (@Composable (Any?) -> String)? = null,
    // NEW: caveats and defaults, shown only in the editor.
    var detail: StringResource? = null,
    ...
)
```

Copy for all three fields follows [COPY](../COPY/README.md): titles up to 24 characters,
values up to 12, summaries up to 48.

### Engines stop mutating shared state

A category holds entries, not prefs. `+PREF` makes a plain entry; an engine attaches its control
to the entry instead of to the pref:

```kotlin
+KITE_SUBTITLE_SCALE.withSlider(25..300) { kite?.setSubtitleScale(it) }
+MPV_INTERPOLATION.enabledWhen { MPV_VIDSYNC.value() != "audio" }
```

The renderer reads the entry's control first and the declaration's second. Nothing global
changes when a room opens, so the same pref can render in the global screen without a control
and in the room with one, and switching engines mid-process cannot leave a dead callback bound
to the previous engine.

### The console layout

Every row is a channel strip: **label on the left, value in a fixed 90dp column on the right,
control after it.** Because the value column is fixed, values line up vertically down the whole
panel and you can read the entire configuration in one downward scan. That is the single
biggest scannability win and it is not something a Material list can do.

Group headings sit in the gutter in `group` type. Groups are separated by a full bleed hairline.
No cards, no elevation, no rounded containers.

### Rows

| Row | Serves | Height | Control |
|---|---|---:|---|
| `ToggleRow` | Boolean, BooleanCallback | 42dp | `On`/`Off` in the value column, then a rocker |
| `StepRow` | MultiChoice under 5 options | 42dp | `‹ value ›` stepper, changes in place |
| `OpenRow` | MultiChoice 5+, TextField, ShowComposable editors | 42dp | value, then a chevron, opens an editor |
| `ColorRow` | ColorPick | 42dp | hex in the value column, then a swatch |
| `ScrubRow` | Slider | 54dp | value beside the label, track full width beneath |
| `ActionRow` | PerformAction, YesNoDialog | 42dp | chevron. Destructive gets a left stripe |

Rules the rows enforce, which the current renderer does not:

- Every text element is bounded by `maxLines` and ellipsis. No row can grow without bound.
- **Text preferences stop being inline.** A field in a row fights the label for width, summons
  the keyboard over the list, and writes to storage on every keystroke. It becomes an `OpenRow`
  leading to an editor with a real field, the explanation, validation, and Cancel and Save. The
  trusted domains popup already works this way and is the model.
- A disabled row (its dependency is off) reads its dependency through the prefs snapshot, so it
  updates the moment the dependency flips. Today the check is a plain function call and the
  row stays stale until something else recomposes it.
- A stored value that no longer matches any option (a network engine saved on one platform and
  read on another) renders as the raw value in the column and the editor selects nothing. Today
  the chooser calls `.first` on the match and crashes when the dialog opens.
- A slider fires its live callback once per drag frame today with no rate limit, straight into
  the engine. The scrub row fires the callback on release and at most every 60 ms while dragging.
- The `BooleanCallback` hook fires once, after the write, from one path. Today it fires from both
  the row click and the switch, before the write.

`StepRow` is worth calling out: for a short option set, stepping in place means changing the
network engine or a language never opens a dialog at all.

### Width

Three classes, from available width, not from which host is drawing. The classes are defined
once in [NAVIGATION](../NAVIGATION/README.md).

| Class | Width | Layout |
|---|---|---|
| Compact | under 480dp | one column, no row icons, editors are sheets |
| Medium | 480 to 839dp | one column capped at 560dp and centred, row icons on |
| Expanded | 840dp and up | two pane, category list at 280dp beside the settings, editors are panels |

Text is capped at 560dp everywhere so line measure stays readable on a phone and on a monitor.
The expanded class removes the navigation state machine entirely on tablet and desktop.

Row icons follow two rules, in this order: the compact class shows none, and in any class a
group whose rows would all take the same icon shows none (the eight haptics rows).

### Hosts

**Global settings become a screen.** Add `Screen.Settings(categoryKey: String? = null)` to the
sealed `Screen` interface and wire it into `AdamScreen`'s `entryProvider`. The gear in the home
bar navigates instead of expanding a drawer. System back works, a category is deep linkable, and
the layout gets the whole viewport. The drawer, its resting height dance and `SettingGridState`
go with it.

**In-room settings become a side sheet.** They must not leave the room, so they stay a panel,
but not a 37% card: the standard room panel from [ROOM_CARDS](../ROOM_CARDS/README.md),
`clamp(320dp, 38% of width, 420dp)` wide, at the same type size as the global screen. The card
slot keeps only its entry point. `settingROOMstyle`, `settingGLOBALstyle` and `SettingStyling`
are deleted, and 13sp, 11sp and 9sp type disappears from the app.

The engine category is inserted by name, after the player category, not at `size - 2` of a
list whose length happens to be four.

### Findability

- **Search** across every category by title and explanation, results as `Category > Setting`.
  The index is built with `remember` after the strings resolve, from the **resolved** category
  list of the host that is showing it (the global screen has 21 prefs, the room has 25 plus the
  active engine's), so engine injected preferences are included.
- **Groups** inside a category. `SettingCategory`'s builder gains an optional `group()`. A bare
  `+PREF` still works and lands in an implicit first group, so no existing category has to
  change.
- `INROOM_HAPTICS` is added to `SETTINGS_ROOM`, and the five `OSD_*` switches get a Notices
  group under Chat, since [STATUS_AND_OSD](../STATUS_AND_OSD/README.md) keeps reading them.
- `MSG_MAXCOUNT`, `AUDIO_DELAY` and `SUBTITLE_DELAY` are either wired or deleted; see
  [CHAT](../CHAT/README.md) for the first, the other two go (each engine has its own delay
  prefs).

### Reset

Both reset rows run `preferences.clear()` today, which also wipes the user id, the saved join
config, the chosen engine, the current and custom themes, the GIF favourites and the undo-seek
choice. Reset becomes scoped: the global reset clears the keys of every global category, the
in-room reset clears every in-room and engine key, and identity, themes, favourites and saved
rooms survive both.

### Tokens

`SettingStyling` is deleted. Rows use FOUNDATION tokens directly. Where a surface genuinely
needs to vary, a `SettingsDensity` carries semantic choices (`showRowIcons`,
`showInlineExplanations`, `contentMaxWidth`) and never font sizes.

## Measured result

The console prototype renders the same real content:

| Category | Before | After | Change |
|---|---:|---:|---|
| Network, 4 prefs | 508dp | 195dp | 2.6x shorter |
| Player settings, 10 prefs, now grouped into 4 | 1189dp | 560dp | 2.1x shorter |

Player settings fits one screen instead of 1.7, with four group headings added rather than
removed.

## Invariants

Things the current code gets right that the rewrite keeps:

- `Pref.Render()` dispatches on the runtime class of `default`, and a pref whose default is
  null has no config and is never rendered. Keep the two rules together.
- `MultiChoice.entries` is a composable lambda because it resolves string resources; a cached
  map must stay composable scoped or a language change stops reaching the labels.
- `SUBTITLE_SIZE` reaches the live player through the room's weak reference and silently does
  nothing from the global screen. The entry model above makes that explicit: the global entry
  has no live callback.
- `DISABLE_FROSTED_GLASS` is deliberately deferred to the next room entry, because the video
  surface type is fixed when the player view is inflated. The row says so in its detail.
- `RECONNECTION_INTERVAL`, `TLS_ENABLE` and `NETWORK_ENGINE` take effect on the next connection.
  Their detail says so.
- The trusted domains editor splits on newlines and commas when reading and joins on newlines
  when saving, which migrates old comma separated values on first save.
- The media directories editor removes a directory from the pref and from
  `MediaAccessRegistry` together; dropping either leaks a persisted grant.
- Four summaries take `appName` as a format argument.

## Phases

**1. Foundation controls.** Rocker, scrub track, stepper, swatch, chevron, field, rule. These
live in `uicomponents/controls` and are used by every other overhaul, so they land first.

**2. Row taxonomy.** The six rows, the two new `SettingConfig` fields, the entry model with
engine overrides, editors, and `SettingComposable` reduced to a dispatcher. Fixes causes 1, 2,
6, 8, 9. Touches no host and no declaration except the four engine overrides.

**3. Real hosts.** `Screen.Settings` and nav wiring, two pane expanded layout, the in-room
panel. Deletes `SettingGridState`, both category cards, the `Layout` enum, `settingROOMstyle`,
`settingGLOBALstyle` and `SettingStyling`. Fixes causes 3, 4, 7.

**4. Findability and reset.** Search and index, `group()`, reattach the orphaned haptics and
notices, scoped reset. Fixes cause 5.

**5. Copy.** Split 97 summaries into a one line summary and an optional detail, per
[COPY](../COPY/README.md). Mechanical, safe a category at a time: where detail is missing the
renderer reuses summary.

**6. Guardrails.** Extend the harness into a golden set over every category at 360, 720 and
1200dp, so a change that reintroduces a seven line row fails visibly.

## Verification

The harness is the test. It renders production composables headlessly with no emulator:

- `shared/src/desktopTest/kotlin/app/preferences/SettingsRenderHarness.kt` renders the current
  system and reports heights.
- `SettingsConsolePrototype.kt` is the design prototype for the console system. It is the
  reference for phase 1 and is deleted when the controls land in `uicomponents`.
- `SettingsRedesignPrototype.kt` was the earlier, Material based proposal. It is deleted with
  phase 1; nothing in it survives.

Each phase ends by re-rendering every category at all three widths and checking that no category
exceeds its budget per preference, and no text element exceeds its `maxLines`.

## Risks

- **Phase 5 is 97 strings.** The slow part, deliberately last, never blocking.
- **Removing inline text fields changes a habit.** Editing a server address takes a tap first.
  Correct trade against fighting a 120dp field, but it is a change.
- **Icons are a real trade.** All 95 declared configs set a hand picked icon and there are 54
  distinct ones. Within a category they repeat. The two icon rules above are one token each to
  flip if the result reads wrong on screen.
- **Engine categories vary at runtime.** Search and grouping must build from the resolved list,
  and the harness renders each engine's category with a stub engine so the goldens cover them.
