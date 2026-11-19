package app.room

import app.protocol.sync.SyncPrefs
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Why this device holds its playback. A held device stays paused on purpose, reports the room's
 * position and takes no corrections. When the last reason goes, it jumps back to the room.
 */
enum class HoldReason {
    /** The room left the screen, and this device does not keep its sound in the background. */
    LeftScreen,

    /** Another app or a call took the sound (Android audio focus). */
    AudioFocus,

    /** The headphones went away. A phone in a pocket must never fall back to its speaker. */
    Route,

    /** The system interrupted the sound, for example a call or Siri on iOS. */
    Interruption,

    /** Background sound ended: the room stayed paused too long, or the system refused it. */
    ListeningEnded,

    /** The connection to the server dropped while the room was away. */
    ConnectionLost,
}

/**
 * The set of [HoldReason]s of one room. Each call says whether it changed the held state, so the
 * caller pauses exactly once on the first reason and re-anchors exactly once on the last.
 */
class PlaybackHolds {
    private val lock = SynchronizedObject()
    private val reasons = mutableSetOf<HoldReason>()

    val held: Boolean get() = synchronized(lock) { reasons.isNotEmpty() }

    fun has(reason: HoldReason): Boolean = synchronized(lock) { reason in reasons }

    /** Adds [reason]. True when this made the device held. */
    fun add(reason: HoldReason): Boolean = synchronized(lock) {
        val was = reasons.isNotEmpty()
        reasons += reason
        !was
    }

    /** Removes [reason]. True when this was the last reason, so the device is free again. */
    fun remove(reason: HoldReason): Boolean = synchronized(lock) {
        reasons.remove(reason) && reasons.isEmpty()
    }

    /** Removes every reason. True when the device was held. */
    fun clear(): Boolean = synchronized(lock) {
        val was = reasons.isNotEmpty()
        reasons.clear()
        was
    }
}

/** How long a room may stay paused while this device listens away, before listening ends. */
val LISTEN_AWAY_PAUSE_GRACE: Duration = 10.minutes

/** How long a lost connection may last while this device listens away, before it holds. */
val LISTEN_AWAY_CONNECTION_GRACE: Duration = 3.seconds

/**
 * Whether this device keeps playing the room's sound when the room leaves the screen. Every
 * condition must hold: the user turned the setting on, the platform can do it, a file is loaded,
 * the engine is ready, and the room is connected (or has no server, in solo mode).
 */
fun keepsSoundAway(
    settingOn: Boolean,
    platformSupports: Boolean,
    hasMedia: Boolean,
    playerReady: Boolean,
    connectedOrSolo: Boolean,
): Boolean = settingOn && platformSupports && hasMedia && playerReady && connectedOrSolo

/**
 * The sync preferences of a device whose room is not on screen. It follows the room: it reports
 * the room's position, like "Don't slow down others", so a stall in a pocket never pulls the room
 * back. Every correction is on, because a follower that cannot catch up drifts without a word.
 */
fun SyncPrefs.whenAway(): SyncPrefs = SyncPrefs(rewind = true, fastForward = true, slowdown = true, dontSlowWithMe = true)
