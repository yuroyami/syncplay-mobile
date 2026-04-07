package app.player.mpv

import app.player.PlayerImpl
import app.room.RoomViewmodel
import platform.UIKit.UIView

/**
 * Factory function reference for instantiating the MPVKit-based player on iOS.
 *
 * ## Why a bridge?
 * MPVKit exposes the raw C libmpv API, which has no Objective-C interoperability.
 * Direct Kotlin/Native calls to libmpv are not possible. Solution: a Swift class
 * subclasses [MpvKitPlayerBridge], implementing each method using libmpv C calls,
 * and Swift registers a factory function at app startup.
 *
 * ## Implementation Architecture
 * The actual MPVKit implementation can be found at:
 * ```
 * iosApp/iosApp/MpvKitBridge.swift
 * ```
 *
 * ## Usage
 * This function reference is initialized by the Swift side during app startup:
 * ```swift
 * MpvKitBridgeKt.instantiateMpvKitPlayer = { viewmodel in
 *     return MpvKitImpl(viewmodel: viewmodel)
 * }
 * ```
 *
 * @see MpvKitPlayerBridge
 * @see PlayerImpl
 */
var instantiateMpvKitPlayer: ((viewmodel: RoomViewmodel) -> PlayerImpl)? = null

/**
 * Abstract bridge class that the Swift side subclasses to provide libmpv functionality.
 *
 * Swift implements each method by calling the corresponding libmpv C API functions.
 * The Kotlin [MpvKitImpl] delegates all player operations to this bridge.
 *
 * Property names match libmpv's property system for clarity:
 * - `time-pos` → [getTimePos] / position in seconds
 * - `duration` → [getDuration] / total duration in seconds
 * - `pause` → [setPaused] / pause state
 * - `speed` → [setSpeed] / playback speed
 * - `aid` / `sid` → [setAudioTrack] / [setSubtitleTrack]
 */
abstract class MpvKitPlayerBridge {

    /** Callback invoked by Swift when an observed property changes. Set by [MpvKitImpl]. */
    var onPropertyChange: ((name: String, value: Any?) -> Unit)? = null

    /** Callback invoked by Swift when a playback event occurs (file loaded, ended, etc). */
    var onEvent: ((eventName: String) -> Unit)? = null

    // ── Lifecycle ────────────────────────────────────────────────────

    /** Create mpv handle, set options, initialize, and set up render context (Metal/MoltenVK). */
    abstract fun create()

    /** Destroy mpv handle and free all resources. */
    abstract fun destroy()

    /** Returns the UIView that contains the Metal rendering surface. */
    abstract fun getPlayerView(): UIView

    // ── Media loading ────────────────────────────────────────────────

    /** Load a local file by absolute path. Equivalent to `mpv_command(["loadfile", path])`. */
    abstract fun loadFile(path: String)

    /** Load a remote URL. Equivalent to `mpv_command(["loadfile", url])`. */
    abstract fun loadURL(url: String)

    // ── Playback control ─────────────────────────────────────────────

    /** Set the `pause` property. true = paused, false = playing. */
    abstract fun setPaused(paused: Boolean)

    /** Get the `pause` property. */
    abstract fun isPaused(): Boolean

    /** Seek to absolute position in seconds. Sets `time-pos` property. */
    abstract fun seekTo(positionSeconds: Double)

    /** Get current position in seconds (`time-pos` property). */
    abstract fun getTimePos(): Double

    /** Get total duration in seconds (`duration` property). */
    abstract fun getDuration(): Double

    /** Set playback speed (`speed` property). 1.0 = normal. */
    abstract fun setSpeed(speed: Double)

    // ── Track management ─────────────────────────────────────────────

    /** Get the number of tracks (`track-list/count`). */
    abstract fun getTrackCount(): Int

    /** Get track type at index: "video", "audio", or "sub" (`track-list/N/type`). */
    abstract fun getTrackType(index: Int): String?

    /** Get track ID at index (`track-list/N/id`). */
    abstract fun getTrackId(index: Int): Int

    /** Get track language at index (`track-list/N/lang`). */
    abstract fun getTrackLang(index: Int): String?

    /** Get track title at index (`track-list/N/title`). */
    abstract fun getTrackTitle(index: Int): String?

    /** Check if track at index is selected (`track-list/N/selected`). */
    abstract fun isTrackSelected(index: Int): Boolean

    /** Set active audio track by ID. Pass -1 or "no" to disable. */
    abstract fun setAudioTrack(id: Int)

    /** Set active subtitle track by ID. Pass -1 or "no" to disable. */
    abstract fun setSubtitleTrack(id: Int)

    /** Add an external subtitle file. Equivalent to `mpv_command(["sub-add", path])`. */
    abstract fun addSubtitleFile(path: String)

    // ── Chapters ─────────────────────────────────────────────────────

    /** Get chapter count (`chapter-list/count`). */
    abstract fun getChapterCount(): Int

    /** Get chapter title at index (`chapter-list/N/title`). */
    abstract fun getChapterTitle(index: Int): String?

    /** Get chapter time offset in seconds at index (`chapter-list/N/time`). */
    abstract fun getChapterTime(index: Int): Double

    /** Jump to chapter by index. Sets `chapter` property. */
    abstract fun setChapter(index: Int)

    // ── Aspect ratio ─────────────────────────────────────────────────

    /** Get current video aspect ratio override (`video-aspect-override`). */
    abstract fun getAspectOverride(): String?

    /** Set video aspect ratio override. Pass "-1" for original. */
    abstract fun setAspectOverride(aspect: String)

    /** Get panscan value (`panscan`). 0.0-1.0. */
    abstract fun getPanscan(): Double

    /** Set panscan value. */
    abstract fun setPanscan(value: Double)

    // ── Subtitles ────────────────────────────────────────────────────

    /** Set subtitle scale (`sub-scale`). 1.0 = default. */
    abstract fun setSubScale(scale: Double)

    // ── Volume ───────────────────────────────────────────────────────

    /** Get current volume (0-100+). */
    abstract fun getVolume(): Int

    /** Set volume. */
    abstract fun setVolume(volume: Int)

    /** Get max volume (typically 100 for mpv, but can be higher). */
    abstract fun getMaxVolume(): Int
}
