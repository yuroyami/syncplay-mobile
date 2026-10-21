# Synkplay

Read [CONTRIBUTING.md](CONTRIBUTING.md) before you change code. Its section 5 lists the traps of each area.

## Gotchas

- `platformCallback` is one global for the whole process, and the two-client test harness swaps it through reflection, so two harness tests cannot run at the same time (#231).
- The visualizer's flashing light warning shows only until the first "Turn on", and neither Reset defaults button brings it back, so run its device check before any other visualizer check (#199).
- Some Syncplay PC translation files hold English text for a key (for example the "set as ready by" lines and `persistent-rooms-notice`), so never copy such a text into a Synkplay translation (#215).
