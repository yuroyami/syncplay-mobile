# Copy and language

Assumes [FOUNDATION](../FOUNDATION/README.md).

Files: `shared/src/commonMain/composeResources/values-en/strings.xml` (the source of truth,
about 500 strings), `values/strings.xml` (generated from `values-en` at build time by
`propagateDefaultStrings()`, never edited by hand), and seven other locales (ar, de, es, fr, pl,
ru, zh) that fall back to English one string at a time.

## What it is

Every word the app says. Titles, summaries, buttons, notices, errors, tips. Most of it was
written one setting at a time over years, so it has no single voice.

## What is wrong

**No voice.** Title Case and sentence case sit next to each other ("Show Custom Skip Button on
Main Screen" above "Seek forward jump amount (seconds)"). Some rows talk to the user, some talk
about the code.

**Summaries are manuals.** 35 of them are over 110 characters and the longest is 266. They give
history, reasons and examples on a row whose only job is to say what the setting is and what it
is set to.

**Units live in the title** ("(seconds)") instead of on the value, where they would sit next to
the number.

**Errors speak protocol.** A failed join says what the socket did, not what to do next.

**English is hardcoded in several composables.** Each surface audit lists its literals; every
surface overhaul moves them into resources as it lands.

## The voice

- Sentence case, everywhere. "Custom skip button", not "Custom Skip Button".
- Short. If a row can be understood from its title, it has no summary.
- Plain words first, the technical word after in brackets if someone might search for it:
  "Secure connection (TLS)".
- Buttons are verbs: "Join", "Copy", "Reset". Never "OK" where a verb fits.
- Numbers are digits, and the unit sits on the value: "10 s", "90 s", "16".
- No exclamation marks, no "please", no jokes in error text.
- No history and no reasons in row copy. "Default is 16" belongs in the editor detail, if
  anywhere.
- Errors say what happened and what to do next, in that order, and never blame the reader.
- No marketing words. The banned list: seamless, robust, powerful, leverage, empower, enhance,
  optimize, elevate, unlock, streamline, intuitive, effortless, delve, and any "not just X, it is
  Y" sentence.

Six real strings, before and after:

| Before | After |
|---|---|
| Show Custom Skip Button on Main Screen | Custom skip button |
| Seek forward jump amount (seconds) | Forward jump, value `10 s` |
| Preferred Subtitle Language | Subtitle language |
| The default skip duration is 90 seconds (1:30 minutes), which is ideal for anime. You can adjust this duration for the custom skip button here. | How far the custom skip button jumps. |
| Display chapter dot markers on the seekbar when available. | Marks chapters on the seekbar. |
| Enabling this will display the custom 'Skip period' button, found in the 'Seek to Position' popup, on the main player screen between the rewind and fast-forward buttons for easier access. | Adds a skip button next to rewind and forward. |

## Three fields per setting

[PREF_SYSTEM](../PREF_SYSTEM/README.md) splits one `summary` into three. The sizes come from
what fits on a 360dp phone in the console rows, not from taste.

| Field | Limit | Where it shows |
|---|---:|---|
| title | 26 characters | the row label, one line; a longer one wraps to two |
| value | 12 characters | the value column: `On`, `Netty`, `English`, `#000000`, `10 s` |
| summary | 48 characters | one `note` line under the title in the editor, or inline when Show descriptions is on |
| detail | no limit | the editor only, below the summary |

Where a title cannot fit 26 characters, the group heading carries the context instead: under a
"Subtitles" heading, "Subtitle language" becomes "Language".

## Resource rules

- Edit `values-en` only. The build copies it into `values`.
- Compose resources are not Android resources. The parser understands `\n`, `\t`, `\uXXXX`
  and `\\` and nothing else. An Android style `\'` or a string wrapped in quotes renders as typed.
- Format arguments are `%1$s` style. Counts use `Res.plurals`, never "1 user(s)".
- New keys are named `<surface>_<thing>_<role>`: `settings_glass_title`,
  `settings_glass_summary`, `settings_glass_detail`. Existing keys keep their names, because the
  seven translations are keyed on them; a rename is a retranslation.
- A new string ships in English only and falls back everywhere. That is fine and expected.
- Nothing user facing lives in a Kotlin string literal. The exception is a value the user typed.

## Phases

1. This document: the voice and the limits. Everything after it is measured against them.
2. Settings copy: every title, value, summary and detail. A category at a time, in the order
   PREF_SYSTEM lands them. Where a detail is missing the renderer reuses the summary.
3. Hardcoded literals into resources, surface by surface, as each overhaul lands.
4. Errors and notices, once [STATUS_AND_OSD](../STATUS_AND_OSD/README.md) and
   [STATES](../STATES/README.md) define which ones exist.

## Verification

A desktopTest reads `values-en/strings.xml` and fails on any `_title` over 26 characters, any
`_summary` over 48, any Android style escape, and any string in the banned list. It runs with
the render goldens.

## Risks

- Shorter titles lose information. The group heading and the editor detail must carry it, so
  copy lands together with the grouping in PREF_SYSTEM, not before.
- Seven translations fall out of step the moment English changes. They fall back per string, so
  a half translated category shows mixed languages until a translator catches up. That is
  accepted; the alternative is never changing the English.
