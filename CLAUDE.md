# Synkplay

Read [CONTRIBUTING.md](CONTRIBUTING.md) before you change code. Its section 5 lists the traps of each area.

## Gotchas

- `platformCallback` is one global for the whole process, and the two-client test harness swaps it through reflection, so two harness tests cannot run at the same time (#231).
- The visualizer's flashing light warning shows only until the first "Turn on", and neither Reset defaults button brings it back, so run its device check before any other visualizer check (device checks discussion #250).
- `./kotlin test` runs only the classes whose names end in `Test` or `Tests`. A test class with another name never runs, so end each test class name with `Test`.
- Some Syncplay PC translation files hold English text for a key (for example the "set as ready by" lines and `persistent-rooms-notice`), so never copy such a text into a Synkplay translation (#215).
- Write the release notes for a person who has the previous release installed. Do not write them from the commit log, because one commit is not one line. The notes are the `## <version>` section of `CHANGELOG.md`, and `.github/scripts/release-body.sh` builds the GitHub release page from that section.
  - Leave out a fix for a fault that the previous release did not have, such as a fault in a feature that is new in this version.
  - Give a new feature one line. Leave out its inner details, such as where a key sits, a colour, or a list of every new message.
  - Leave out a change that is too small for a user to notice.
  - Give each change its true size and its true name. A fix is a fix, and a small change is "slightly improved".
  - Say what the user gets, not how the change is built. For a library update, say what the update brings.
  - Say who a change does not apply to, for example the official servers or a user who changed the setting.
  - Put lines that share a context together. Put a removed thing in the same line as its replacement.
  - Put the user-facing changes first. Put the library updates, the build, the licence and the contributor lines last, under "Dependency Updates".
  - End a line with the numbers of its issues, pull requests and discussions. Write "reported by" for a bug report, "issue by" for a request and "fixed by" for a pull request.
  - Never write a sentence in the owner's voice, such as a request for feedback. The owner writes the intro.
