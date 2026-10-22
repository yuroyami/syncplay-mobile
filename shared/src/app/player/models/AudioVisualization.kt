package app.player.models

internal fun shouldShowAudioVisualization(enabled: Boolean, audioSelected: Boolean, videoSelected: Boolean): Boolean =
    enabled && audioSelected && !videoSelected

/**
 * The controls of an engine's audio visualizer while it draws. The reads are Compose snapshot
 * state, so a composable that shows them updates with the engine.
 */
interface VisualizerControls {
    /** Every drawing the engine offers, by name, in the engine's order. */
    val drawings: List<String>

    /** The index in [drawings] of what is on screen now. */
    val showing: Int

    /** Puts [drawings]`[index]` on screen at once and remembers it as the viewer's pick. */
    fun show(index: Int)

    /**
     * Whether the engine's director (its automatic drawing picker) changes the drawing with the
     * music.
     */
    var directed: Boolean
}
