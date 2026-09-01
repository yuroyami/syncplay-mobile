# States

Assumes [FOUNDATION](../FOUNDATION/README.md).

Not a single file. Every surface has moments when it has nothing to show, is waiting, or has
failed, and today each one improvises or shows a blank rectangle. This folder gives those
moments one vocabulary, and lists every state the surface audits found the code handling with
no design behind it.

## The vocabulary

| State | Form |
|---|---|
| empty | one `note` line in `inkDim` saying what would be here, and at most one Secondary action. No illustration, no icon |
| loading | the Progress bar on the container's top rim. The content underneath stays; nothing is replaced by a spinner |
| error | a `bad` 2dp stub, one `note` line saying what happened and what to do, and one action, usually Retry |
| offline | a `warn` Notice. The surface itself does not change |
| disabled | everything at `disabled`, the reason in the row's detail |
| waiting on a person | a `note` line naming who or what is awaited, for readiness and controlled rooms |

Rules: never a blank rectangle, never a spinner, never a state with two sentences or two
actions, and never a state that looks like another state (an error is not an empty list).

## The states, by surface

Handled means the code does something today; the column says what the design does.

### Home

| State | Today | Design |
|---|---|---|
| saved config arriving | defaults render, then fields re-key | unchanged; no visible loading |
| validation failure | a snackbar | inline under the field, in `bad` |
| unavailable engine chosen | refused with a snackbar | refused with a Notice |
| shortcut arriving | joins at once | unchanged |

### Settings

| State | Today | Design |
|---|---|---|
| row disabled by a dependency | 38 percent alpha, not reactive | `disabled`, reactive, reason in detail |
| stored value matches no option | blank label, a crash when the chooser opens | raw value in the column, editor selects nothing |
| engine category absent (AVPlayer) | one fewer card, unexplained | nothing shown; the search index simply has fewer entries |
| in-room category loading | the whole card is empty until the engine answers | Progress on the panel rim |
| no trusted domains | identical row, hardcoded placeholder | value column `None`, editor empty state with an Add row |
| no media folders | a bare list | empty state with one Add action |

### Room

| State | Today | Design |
|---|---|---|
| no media | artwork, add button as a labelled call to action, transport hidden, gestures off | unchanged in substance; artwork simplified per ROOM_SHELL |
| media loading or a link resolving | a notice only | Progress on the transport's rim until the engine reports a duration or fails |
| unknown or zero duration (live) | track range collapses, total shows `00:00`, a dead `???` branch | total shows `--:--`, track disables scrubbing, jump buttons still work |
| audio only | the video HUD over black | artwork with the file name, transport as usual |
| connecting or waiting to reconnect | rendered as disconnected | `accent` square, "Connecting" or "Reconnecting in 5 s" |
| disconnected | red word | `bad` square plus one `note` line; reconnection stays automatic |
| alone in the room | a warning category exists | the roster's empty line: "Nobody else is here yet" |
| paused by readiness | play converts to "set me ready" | the play key shows the waiting state and the Notice says who is not ready |
| solo mode | ten scattered checks | the frame drops status, roster, ready and chat; notices still render |
| picture in picture | status and panels hidden, transport and readout not | video only |
| background | playback paused unless in picture in picture | unchanged |
| keyboard open | two stage tap, tap shields | unchanged |
| chat disabled by the server | nothing rendered, tab still present | the chat cell is absent from the rail |
| empty chat | a transparent empty list | "Say hello" in `note`, one line |
| GIF search empty | "no results" | unchanged |
| GIF request failed or offline | looks identical to empty | error state with Retry |
| subtitle quota reached | a line inside the sheet with the reset time | unchanged in substance, on the error form |
| unsupported link | silent fallback, then a generic error | recognised at paste, said outright |
| resolver switched off | silent | said in the link route's note line |

### Playlist

| State | Today | Design |
|---|---|---|
| empty | an empty list under a button row | "No files yet" with one Add action |
| entry missing locally | an icon | the state glyph and a `warn` word on the expanded row |
| a remembered folder lost its grant | invisible until a file fails | the folder row in `warn` with a Re-grant action |

### Theming

| State | Today | Design |
|---|---|---|
| no custom themes | the add tile stands in | the custom group shows its empty line under the heading |
| save refused as a duplicate | a snackbar | a Notice saying the theme already exists |

### Server host

| State | Today | Design |
|---|---|---|
| stopped, starting, running, error | dot plus word | the status row per SERVER_HOST |
| port in use | a generic failure line in the log | "Port N is taken" in the status row |
| zero clients | "0 client(s)" | the count in the value column, and the status label reads "Running, waiting" |
| no LAN address | the card silently disappears | the row says "No network address found" |
| public address unavailable | "Could not determine" for one frame, then fetching | Progress on the row, then the row goes away quietly |
| empty log | the section is hidden | hidden, unchanged |

## Phases

There is no phase list of its own. Each surface folder's phases carry the states above; this
folder is the checklist the harness goldens are built from: every state in these tables gets a
golden, so a surface cannot land without its empty, loading and error renders.
