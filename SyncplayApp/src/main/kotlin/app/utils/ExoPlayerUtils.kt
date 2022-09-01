package app.utils

import android.graphics.Color
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import app.R
import app.controllers.activity.RoomActivity
import app.protocol.JsonSender
import app.utils.RoomUtils.string
import app.utils.UIUtils.displayInfo
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import java.io.IOException
import java.util.*


/** Separating Exoplayer-related methods here to make our code cleaner, and more maintainable.
 * Any function that is here needs no more coding and is ready to deploy for all Android APIs.
 */
object ExoPlayerUtils {

    /** Injects a certain media file into ExoPlayer **/
    fun RoomActivity.injectVideo(mediaPath: String) {
        try {
            Log.e("j", "jjjj")
            /** This is the builder responsible for building a MediaItem component for ExoPlayer **/
            val vidbuilder = MediaItem.Builder()

            /** Seeing if we have any loaded external sub **/
            if (p.file?.externalSub != null) {
                runOnUiThread {
                    hudBinding.exoSubtitle.setImageDrawable(
                        AppCompatResources.getDrawable(
                            this,
                            R.drawable.ic_subtitles
                        )
                    )
                }
                vidbuilder.setUri(mediaPath)
                    .setSubtitleConfigurations(Collections.singletonList(p.file!!.externalSub!!))
            } else {
                vidbuilder.setUri(mediaPath)
            }

            /** Now finally, we build it **/
            val vid = vidbuilder.build()

            /** Injecting it into ExoPlayer and getting relevant info **/
            runOnUiThread {
                myExoPlayer?.setMediaItem(vid) /* This loads the media into ExoPlayer **/
                myExoPlayer?.prepare()

                /** Goes back to the beginning */
                p.currentVideoPosition = 0.0

                /** Seeing if we have to start over **/
                if (startFromPosition != (-3.0).toLong()) myExoPlayer?.seekTo(startFromPosition)

                /** Removing artwork */
                binding.syncplayArtworkDelegate.visibility = View.GONE
                binding.vidplayer.videoSurfaceView?.visibility = View.VISIBLE
                binding.vidplayer.setShutterBackgroundColor(Color.BLACK)
            }

            if (!p.connected) {
                p.sendPacket(JsonSender.sendFile(p.file!!, this))
            }
            for (i in (0..5)) {
                sharedplaylistPopup.update()
            }
        } catch (e: IOException) {
            throw RuntimeException("Invalid asset folder")
        }
    }

    /** Used for applying any registered subtitle/audio selecions after, for example, going back
     * to the RoomActivity after closing the app for far too long (restoring instance state).*/
    fun RoomActivity.applyLastOverrides() {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (myExoPlayer != null) {
            if (myExoPlayer!!.trackSelector != null) {
                val builder = myExoPlayer!!.trackSelector!!.parameters.buildUpon()

                var newParams = builder.build()

                if (lastAudioOverride != null) {
                    newParams = newParams.buildUpon().addOverride(lastAudioOverride!!).build()
                }
                if (lastSubtitleOverride != null) {
                    newParams = newParams.buildUpon().addOverride(lastSubtitleOverride!!).build()
                }
                myExoPlayer?.trackSelector?.parameters = newParams
            }
        }
    }

    /** This pauses playback on the right thread **/
    fun RoomActivity.pausePlayback() {
        runOnUiThread {
            myExoPlayer?.pause()
        }
    }

    /** This resumes playback on the right thread, and hides ExoPlayer's HUD/Controller UI**/
    fun RoomActivity.playPlayback() {
        runOnUiThread {
            myExoPlayer?.play()
            SyncplayUtils.hideSystemUI(this, false)
        }
    }

    /** Checks for available sub tracks, shows 'em in a popup, then applies a selection **/
    fun RoomActivity.subtitleSelect(ccButton: ImageButton) {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (p.file == null) return
        if (p.file!!.subtitleTracks.isEmpty()) {
            displayInfo(getString(R.string.room_sub_track_notfound))
            runOnUiThread {
                ccButton.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_subtitles_off
                    )
                )
            }
        } else {
            runOnUiThread {
                ccButton.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.ic_subtitles
                    )
                )
            }
            val popup = PopupMenu(this, ccButton)
            popup.menu.add(0, -999, 0, getString(R.string.room_sub_track_disable))
            for (subtitleTrack in p.file!!.subtitleTracks) {
                /* Choosing a name for the sub track, a format's label is a good choice */
                val name = if (subtitleTrack.format?.label == null) {
                    getString(R.string.room_track_track)
                } else {
                    subtitleTrack.format?.label!!
                }

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    p.file!!.subtitleTracks.indexOf(subtitleTrack),
                    0,
                    "$name [${(subtitleTrack.format?.language).toString().uppercase()}]"
                )

                /* Making the popup menu item checkable */
                item.isCheckable = true

                /* Now to see whether it should be checked or not (whether it's selected) */
                item.isChecked = subtitleTrack.selected
            }

            popup.setOnMenuItemClickListener { menuItem: MenuItem ->
                val builder = myExoPlayer?.trackSelector?.parameters?.buildUpon()

                /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
                myExoPlayer?.trackSelector?.parameters =
                    builder?.clearOverridesOfType(C.TRACK_TYPE_TEXT)!!.build()
                lastSubtitleOverride = null

                /* Now, selecting our subtitle track should one be selected */
                if (menuItem.itemId != -999) {
                    lastSubtitleOverride = TrackSelectionOverride(
                        p.file!!.subtitleTracks[menuItem.itemId].trackGroup!!,
                        p.file!!.subtitleTracks[menuItem.itemId].index
                    )
                    myExoPlayer?.trackSelector?.parameters =
                        builder.addOverride(lastSubtitleOverride!!).build()
                }

                /** Show an info that audio track has been changed **/
                displayInfo(string(R.string.room_sub_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()

        }
    }

    /** Checks for available audio tracks, shows 'em in a popup, then applies a selection **/
    fun RoomActivity.audioSelect(audioButton: ImageButton) {
        p.file?.analyzeTracks(myExoPlayer!!)
        if (p.file == null) return
        if (p.file!!.audioTracks.isEmpty()) {
            displayInfo(getString(R.string.room_audio_track_not_found)) /* Otherwise, no audio track found */
        } else {
            val popup =
                PopupMenu(this, audioButton) /* Creating a popup menu, anchored on Audio Button */

            /** Going through the entire audio track list, and populating the popup menu with each one of them **/
            for (audioTrack in p.file!!.audioTracks) {
                /* Choosing a name for the audio track, a format's label is a good choice */
                val name = if (audioTrack.format?.label == null) {
                    getString(R.string.room_track_track)
                } else {
                    audioTrack.format?.label!!
                }

                /* Now creating the popup menu item corresponding to the audio track */
                val item = popup.menu.add(
                    0,
                    p.file!!.audioTracks.indexOf(audioTrack),
                    0,
                    "$name [${(audioTrack.format?.language).toString().uppercase()}]"
                )

                /* Making the popup menu item checkable */
                item.isCheckable = true

                /* Now to see whether it should be checked or not (whether it's selected) */
                item.isChecked = audioTrack.selected
            }

            popup.setOnMenuItemClickListener { menuItem: MenuItem ->

                val builder = myExoPlayer?.trackSelector?.parameters?.buildUpon()

                /* First, clearing our audio track selection */
                myExoPlayer?.trackSelector?.parameters =
                    builder?.clearOverridesOfType(C.TRACK_TYPE_AUDIO)!!.build()

                lastAudioOverride = TrackSelectionOverride(
                    p.file!!.audioTracks[menuItem.itemId].trackGroup!!,
                    p.file!!.audioTracks[menuItem.itemId].index
                )
                val newParams = builder.addOverride(lastAudioOverride!!).build()

                myExoPlayer?.trackSelector?.parameters = newParams

                /** Show an info that audio track has been changed **/
                displayInfo(string(R.string.room_audio_track_changed, menuItem.title.toString()))
                return@setOnMenuItemClickListener true
            }

            // Show the popup menu.
            popup.show()
        }
    }


}