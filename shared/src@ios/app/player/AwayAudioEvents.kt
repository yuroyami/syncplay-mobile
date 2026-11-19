package app.player

import app.room.HoldReason
import app.room.RoomViewmodel
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSessionRouteChangeReasonKey
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

/**
 * The iOS side of background sound (#125), shared by every engine. While a room plays away from
 * the screen, a call or Siri holds this phone until the system says it may resume, and lost
 * headphones hold it until the user comes back. A phone in a pocket never falls back to its
 * speaker, and the room never hears any of this.
 */
object AwayAudioEvents {

    private var viewmodel: RoomViewmodel? = null
    private var observers: List<NSObjectProtocol> = emptyList()

    /** Starts ([active]) or ends watching for [room]. Main thread only. */
    fun update(room: RoomViewmodel, active: Boolean) {
        if (active) start(room) else if (viewmodel === room) stop()
    }

    private fun start(room: RoomViewmodel) {
        viewmodel = room
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        observers = listOf(
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, queue) { note ->
                val ui = viewmodel?.uiState ?: return@addObserverForName
                val info = note?.userInfo ?: return@addObserverForName
                when ((info[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedLongValue) {
                    AVAudioSessionInterruptionTypeBegan -> ui.hold(HoldReason.Interruption)
                    AVAudioSessionInterruptionTypeEnded -> {
                        val options = (info[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedLongValue ?: 0uL
                        if (options and AVAudioSessionInterruptionOptionShouldResume != 0uL) ui.release(HoldReason.Interruption)
                    }
                }
            },
            center.addObserverForName(AVAudioSessionRouteChangeNotification, null, queue) { note ->
                val ui = viewmodel?.uiState ?: return@addObserverForName
                val reason = (note?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
                if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable && ui.isAway) ui.hold(HoldReason.Route)
            },
        )
    }

    private fun stop() {
        // A held phone keeps watching, so the end of a call can still bring the sound back.
        val ui = viewmodel?.uiState
        if (ui != null && ui.isAway && ui.holds.has(HoldReason.Interruption)) return
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers = emptyList()
        viewmodel = null
    }
}
