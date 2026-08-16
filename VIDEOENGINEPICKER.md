# Video Engine Wheel Picker

## Goal

Replace the home-screen engine picker with an in-place horizontal wheel. No popup, no dropdown,
no extra screen. The owner explicitly rejected the popup approach currently sitting uncommitted
in the working tree; the design-in-place philosophy stays, only the split-button shape goes.

Think date/time wheel picker, but horizontal and for engines: the selected engine sits in the
center at full size inside a side-dissolving spotlight capsule, neighbors peek from the sides
smaller, dimmer and turned away in depth, and the strip fades to transparency at both edges.
Scrolling snaps; whatever settles in the center IS the selection, with a haptic click per
engine that crosses the center.

## Current state of the working tree (uncommitted)

- `shared/src/commonMain/kotlin/app/home/components/HomeEngineSelector.kt` = the rejected
  popup-based picker. Replace its contents entirely (or delete it and create
  `HomeEngineWheel.kt`; if renaming, update the import and call in `HomeScreen.kt`).
- `HomeAnimatedEngineButtonGroup.kt` is already `git rm`'d. Leave it dead.
- `HomeScreen.kt` already calls the component at the right spot (inside the 0.75-width column,
  under the `connect_choose_video_engine` leading title, above the experimental warning text).
  Keep that slot and the callback contract exactly:
  `engines: List<PlayerEngine>, selectedEngine: String, onSelectEngine: (PlayerEngine) -> Unit`.
  The caller handles availability (snackbar on unavailable) and persists `PLAYER_ENGINE`.
- Strings added in `values-en/strings.xml`: keep `connect_engine_badge_default`,
  `connect_engine_badge_experimental`, `connect_engine_badge_unavailable`,
  `connect_engine_experimental_note` (already used by HomeScreen). Remove
  `connect_engine_picker_title` (popup-only, will be unused).
  `values/strings.xml` is regenerated from values-en by `propagateDefaultStrings()` at build
  time; do not hand-edit it.

## The wheel

One composable, commonMain, in `app/home/components/`.

**Layout.** `BoxWithConstraints` (or `Layout`) wrapping a `LazyRow`:

- Fixed item width (~112dp) so `contentPadding = (containerWidth - itemWidth) / 2` horizontally
  centers the first and last items. Height ~72dp.
- `flingBehavior = rememberSnapFlingBehavior(lazyListState)` for center snapping.
- Every item: engine icon (32dp) on top, name under it (labelMedium, maxLines 1). Under the
  name, at most ONE badge, priority: Unavailable (error tint) > Experimental (error tint) >
  Default (primary tint). Small 6dp-corner chip like the popup version had. One badge, not a
  row: wheel items are narrow.

**Center emphasis by scroll distance, not by selected flag.** Per item, compute distance of the
item's center from the viewport center from `lazyListState.layoutInfo` and derive:

- scale: lerp 1.0 (center) -> 0.78 (one slot away), clamp.
- alpha: lerp 1.0 -> 0.35.
- Apply in `Modifier.graphicsLayer` reading the state INSIDE the lambda (draw-phase read, no
  recomposition per frame; same law RoomSeekbar and the KiteVideo work follow).

**Edge fade.** On the LazyRow:
`Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent { drawContent(); horizontal gradient Transparent->Black->Black->Transparent with BlendMode.DstIn }`
(~24dp fade zones). This is the wheel look: entries dissolve at the edges instead of clipping.

**Chevrons: rejected (2026-08-16).** The first build shipped animated edge chevrons; the owner
called them out as noise and they are gone. The scroll affordance is now the wheel itself:

- Barrel depth: each item gets `rotationY = signedCenterDistance * 42deg` plus a slight inward
  `translationX`, with `cameraDistance = 9dp * density`, so neighbors visibly turn away like a
  drum seen from outside. Tilted, half-faded neighbors ARE the "more this way" sign.
- Haptic detent: `HapticFeedbackType.SegmentTick` each time a new index crosses the center
  during a scroll (initial composition swallowed).
- Tapping a visible neighbor item animate-scrolls it to center (one `animateScrollToItem`).

**Selection semantics.**

- On first composition: `LaunchedEffect(selectedEngine)` -> `scrollToItem` centering the
  selected engine (instant, no animation, before first frame matters).
- Selection commits when scrolling SETTLES: observe
  `snapshotFlow { lazyListState.isScrollInProgress }`, on false compute the centered index; if
  it differs from `selectedEngine`, call `onSelectEngine(engines[centeredIndex])`.
- Unavailable engine handling: the caller already snackbars and refuses to persist. The wheel
  must then snap BACK: since `selectedEngine` (a `watchPref` value) will not change, re-run the
  centering effect; keying the LaunchedEffect on `selectedEngine` plus a settle counter is
  enough. Verify this revert visually (exoOnly flavor has unavailable engines; on the full
  flavor all listed engines are available, so simulate by temporarily forcing
  `isAvailable=false` in a local run if needed; do not commit that).
- Selection change should NOT fire during the initial centering scroll (guard with a
  `programmaticScroll` flag around scrollToItem calls).

**TV / D-pad.** The wheel itself gets one `tvFocusable` ring (24dp rounded shape, same as the
old group). While focused, left/right D-pad events move selection by one
(`onPreviewKeyEvent` on DirectionLeft/DirectionRight -> animateScrollToItem neighbor). This
preserves the TvFocus system (do-not-bulldoze list).

**Container: borderless (2026-08-16).** The hairline gradient outline shipped and was rejected
by the owner. The wheel now has no container chrome at all. What marks the control instead is
the selection slot: a fixed capsule (128x84dp, 22dp corners, `surfaceContainerHighest` at 0.55
alpha) behind the center position, whose sides dissolve to fully transparent via a horizontal
gradient (opaque between the 30% and 70% stops). It reads as light on the slot, not a box. The
24dp rounded shape survives only as the `tvFocusable` focus-ring shape.

## What NOT to do

- No popup/dialog/dropdown/bottom sheet. Nothing opens.
- No vertical wheel (form is vertical; a horizontal strip is the only shape that fits the slot).
- Do not touch `PLAYER_ENGINE` persistence or the availability snackbar (caller owns them).
- Do not re-add the old weighted split-button code.
- Gradient policy: gradient is allowed only for the container hairline border (identity), not
  for item backgrounds or text.

## Verify

1. `./gradlew :shared:compileAndroidMain :shared:compileKotlinIosArm64 :shared:compileKotlinDesktop`.
2. Emulator (`emulator -avd Pixelu16KB`, wait for `sys.boot_completed`, then
   `ANDROID_SERIAL=emulator-5554 ./gradlew :androidApp:installFullDebug` FROM THE REPO ROOT):
   - Full debug flavor shows 5 entries (ExoPlayer, mpv, VLC, KitePlayer, Kite Compose).
   - Selected engine centered on launch; left/right neighbors peeking, faded.
   - Scroll: snap lands an engine in the center, pref updates (relaunch app, still centered).
   - Chevrons: both visible mid-list; the left one disappears when ExoPlayer is centered, the
     right one when Kite Compose is centered.
   - Screenshot each state (`adb exec-out screencap -p`), LOOK at them; the first popup
     iteration shipped a see-through background that only a screenshot caught.
3. Experimental warning under the wheel still appears for experimental engines (HomeScreen
   logic, unchanged).
