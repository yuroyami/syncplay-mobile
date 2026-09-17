package app.player.models

internal fun shouldShowAudioVisualization(enabled: Boolean, audioSelected: Boolean, videoSelected: Boolean): Boolean =
    enabled && audioSelected && !videoSelected
