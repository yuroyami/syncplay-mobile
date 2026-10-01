package app.player.kite

import io.github.yuroyami.kiteplayer.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The video tab changes KitePlayer's track only for a different stream. KitePlayer reopens
 * the stream and seeks back for every video track change, which stopped the sound for about a
 * second each time the visualizer took the place of the picture, or gave it back.
 */
class KiteVideoSwitchTest {

    @Test
    fun videoOffOnlyHidesThePicture() {
        assertEquals(VideoPick.Hide, videoPickOf(requested = null, playing = TrackId(0)))
        assertEquals(VideoPick.Hide, videoPickOf(requested = null, playing = null))
    }

    @Test
    fun thePlayingStreamOnlyShowsThePicture() {
        assertEquals(VideoPick.Show, videoPickOf(requested = TrackId(0), playing = TrackId(0)))
    }

    @Test
    fun anotherStreamChangesTheEngineTrack() {
        assertEquals(VideoPick.Switch, videoPickOf(requested = TrackId(1), playing = TrackId(0)))
        assertEquals(VideoPick.Switch, videoPickOf(requested = TrackId(0), playing = null))
    }

    @Test
    fun decodingParksOnlyWhenNothingShowsInThePicturesPlace() {
        assertTrue(shouldParkVideo(hasVideo = true, pictureShown = false, visualizerDrawing = false))
        assertFalse(shouldParkVideo(hasVideo = true, pictureShown = false, visualizerDrawing = true))
        assertFalse(shouldParkVideo(hasVideo = true, pictureShown = true, visualizerDrawing = false))
        assertFalse(shouldParkVideo(hasVideo = false, pictureShown = false, visualizerDrawing = false))
    }
}
