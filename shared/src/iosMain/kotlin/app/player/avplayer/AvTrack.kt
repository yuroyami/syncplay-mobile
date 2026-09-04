package app.player.avplayer

import app.player.PlayerImpl
import app.player.models.Track
import app.player.models.TrackTrait
import platform.AVFoundation.AVMediaCharacteristicDescribesVideoForAccessibility
import platform.AVFoundation.AVMediaCharacteristicTranscribesSpokenDialogForAccessibility
import platform.AVFoundation.AVMediaSelectionGroup
import platform.AVFoundation.AVMediaSelectionOption
import platform.AVFoundation.hasMediaCharacteristic

/**
 * [Track] carrying the AVFoundation media selection option and its group, both required to
 * switch tracks via the media selection API.
 */
class AvTrack(
    val sOption: AVMediaSelectionOption,
    val sGroup: AVMediaSelectionGroup,
    override val name: String,
    override val type: PlayerImpl.TrackType?,
    override val index: Int,
    override val selected: Boolean
): Track() {

    override val language: String? get() = sOption.extendedLanguageTag

    /** AVFoundation states the purpose as a media characteristic on the option itself. */
    override val trait: TrackTrait?
        get() = when {
            sOption.hasMediaCharacteristic(AVMediaCharacteristicTranscribesSpokenDialogForAccessibility) -> TrackTrait.ACCESSIBILITY
            sOption.hasMediaCharacteristic(AVMediaCharacteristicDescribesVideoForAccessibility) -> TrackTrait.ACCESSIBILITY
            else -> null
        }
}
