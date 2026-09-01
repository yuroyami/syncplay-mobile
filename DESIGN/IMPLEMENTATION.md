# Implementation

How the folders turn into code: where things go, in what order, how each step is proven, and
how it is committed.

## Where things go

```
shared/src/commonMain/kotlin/app/
  theme/Tokens.kt                    Space, Radius, Type, Motion, Palette, LocalPalette, Tier
  theme/AppTypography.kt             shrinks to the Material bridge (roles pointed at Type), then goes
  uicomponents/controls/             Rocker, ScrubTrack, Stepper, Swatch, Chevron, Field, Rule, Row,
                                     GlyphButton, Segmented, Tag, Progress, Handle, Actions
  uicomponents/frames/               ScreenFrame, Modal, PanelFrame, Notice
  uicomponents/GlassSurface.kt       gains Modifier.surface(tier, shape); loses shape and material later
  room/RoomFrame.kt                  the four docks and the HUD policy
  preferences/rows/                  ToggleRow, StepRow, OpenRow, ColorRow, ScrubRow, ActionRow, editors
  preferences/settings/SettingsScreen.kt   the Screen.Settings destination and the in-room panel body

shared/src/desktopTest/kotlin/app/design/
  DesignHarness.kt                   provides every CompositionLocal, the datastore, the palette
  DesignLint.kt                      the four source checks from FOUNDATION
  *Golden.kt                         one per surface: renders at 360, 720, 1200dp and font scale 1.0, 1.3
```

Every new file is Kotlin 2.4: explicit backing fields instead of `_name` pairs, `Enum.entries`,
`data object` in sealed hierarchies, and context parameters where a composable would otherwise
thread the same dependency through every call.

## The order

Each step compiles on all three targets and passes the harness before the next starts. Steps
marked with a gate cannot be reordered.

| Step | Folder and phase | Gate |
|---|---|---|
| 1 | FOUNDATION: `Tokens.kt`, `LocalPalette`, `Type`, Material bridge | everything below reads it |
| 2 | FOUNDATION controls with semantics and focus (ACCESSIBILITY 1, TV 2) | every row and frame uses them |
| 3 | GLASS 1: `Modifier.surface(tier, shape)` beside the old API | the radius change waits on this |
| 4 | TEXT_AND_ICONS 1: radii and roles available; `appShapes` still Material | |
| 5 | NAVIGATION 1 and 2: `ScreenFrame`, `Screen.Settings`; POPUPS 1: `Modal` | every page and popup |
| 6 | PREF_SYSTEM 1 to 4: rows, entries, hosts, search, scoped reset | first visible payoff |
| 7 | TRANSPORT 1: the one seek path | GESTURES 1 and 2 build on it |
| 8 | TRANSPORT 2 to 6: seekbar, split, controls, chapters, play key | |
| 9 | ROOM_SHELL 1 to 5: frame, docks, rail, HUD policy, PiP, solo | ROOM_CARDS, STATUS, CHAT sit in its docks |
| 10 | STATUS_AND_OSD, ROOM_CARDS, CHAT, GESTURES 3 and 4, MEDIA_INTAKE | any order |
| 11 | HOME, THEMING, SERVER_HOST | any order; HOME 3 waits on step 6 for the settings glyph |
| 12 | POPUPS 2 to 4: every remaining popup and menu; delete the wrappers | |
| 13 | GLASS 3 and 4, TEXT_AND_ICONS 3: delete old glass API, `appTypography`, `appShapes`, fonts | after the last consumer moves |
| 14 | NAVIGATION 3, 5, 6: back, transitions, width classes; DESKTOP; TV 3 and 4; ACCESSIBILITY 3 and 4 | |
| 15 | COPY 2 to 4; TEXT_AND_ICONS 4: the icon set | slow, never blocking |

BUGS.md items land inside the step that rebuilds their surface, never as a separate sweep, so
each fix ships with the golden that proves it.

## How each step is proven

Compile, in this order, because the first is the fastest and catches most of it:

```bash
./gradlew :shared:compileKotlinDesktop
```

```bash
./gradlew :shared:compileFullDebugKotlinAndroid
```

```bash
./gradlew :shared:compileKotlinIosArm64
```

Then the harness, which is where the design is actually checked:

```bash
./gradlew :shared:desktopTest --tests 'app.design.*'
```

It fails on: a lint violation, a text element over its `maxLines`, a category over its height
budget, any text under 11sp, and any golden whose measured size moved. The PNGs it writes are
the evidence; the ones worth keeping are copied into the folder's `evidence/` directory by hand
when that folder lands, and the rest stay in `build/`.

Before any gradle run, check for stray Java daemons from other sessions and stop them; parallel
sessions have left dozens behind before. After a step lands, the manual checklist for what the
harness cannot see: a cutout device in both orientations with the keyboard open, TalkBack or
VoiceOver over the changed surface, and the key map on desktop.

## Commits

One commit per folder phase, on the current branch, no pull requests, no co-author lines. The
subject reads like the existing history: what Synkplay does now, in one sentence, not what the
diff did. A phase that is a pure move (the control panel split, the frame refactor) is its own
commit so the diff can be read as a move. The first commit of the whole effort checkpoints the
in-flight tree as found, as was done before the last engine wiring.

## When something in the docs turns out wrong

The audits were thorough but they are a snapshot. If a folder's claim does not match the code
when the step is reached, the code wins, the folder is corrected in the same commit, and the
invariant or bug list is updated if the finding belongs there.
