package app.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import app.R
import app.ui.activities.WatchActivity
import app.ui.compose.ComposeUtils.FancyText
import app.ui.compose.ComposeUtils.FancyText2
import app.ui.compose.ComposeUtils.RoomPopup
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride

/** Let's keep all popup composables in one place. We can include them directly into WatchActivityUI class,
 * but my Android Studio is literally suffering from lag just because the class is huge.
 * All of this popups use a theme-customized Dialog composable. */
object ComposePopups {

    /** A popup that appears after clicking the tracks button (to select audio and subtitle tracks). */
    @Composable
    fun WatchActivity.MediaTracksPopup(visibilityState: MutableState<Boolean>) {
        return RoomPopup(
            dialogOpen = visibilityState.value,
            widthPercent = 0.71f,
            heightPercent = 0.87f,
            strokeWidth = 0.5f,
            cardBackgroundColor = Color.DarkGray,
            onDismiss = { visibilityState.value = false }
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {

                val (title, audiocard, subtitlecard, audiocardsubtext, subtitlecardsubtext) = createRefs()

                FancyText2(
                    modifier = Modifier.constrainAs(title) {
                        top.linkTo(parent.top)
                        end.linkTo(parent.end)
                        start.linkTo(parent.start)
                    },
                    string = "Tracks",
                    solid = Color.Black,
                    size = 19f,
                    font = Font(R.font.directive4bold)
                )

                Card(
                    modifier = Modifier
                        .constrainAs(audiocard) {
                            top.linkTo(title.bottom, 12.dp)
                            absoluteLeft.linkTo(parent.absoluteLeft)
                            absoluteRight.linkTo(subtitlecard.absoluteLeft)
                            bottom.linkTo(audiocardsubtext.top, 12.dp)
                            width = Dimension.percent(0.42f)
                            height = Dimension.fillToConstraints
                        },
                    shape = RoundedCornerShape(size = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        for ((index, it) in media?.audioTracks?.withIndex() ?: listOf()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = it.selected.value, onCheckedChange = { it2 ->
                                    for (track in media?.audioTracks!!) {
                                        track.selected.value = false
                                    }
                                    it.selected.value = it2

                                })

                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = (if (it.format?.label == null) {
                                        getString(R.string.room_track_track)
                                    } else {
                                        it.format?.label!!
                                    })
                                            + (it.format?.language).toString().uppercase(),
                                    color = Color.LightGray
                                )

                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.constrainAs(subtitlecard) {
                        top.linkTo(title.bottom, 12.dp)
                        absoluteLeft.linkTo(audiocard.absoluteRight)
                        absoluteRight.linkTo(parent.absoluteRight)
                        bottom.linkTo(subtitlecardsubtext.top, 12.dp)
                        width = Dimension.percent(0.42f)
                        height = Dimension.fillToConstraints
                    },
                    shape = RoundedCornerShape(size = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        for ((index, it) in media?.subtitleTracks?.withIndex() ?: listOf()) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = it.selected.value, onCheckedChange = { it2 ->
                                    for (track in media?.subtitleTracks!!) {
                                        track.selected.value = false
                                    }
                                    it.selected.value = it2

                                    val builder = myExoPlayer.trackSelector?.parameters?.buildUpon()

                                    /* First, clearing our subtitle track selection (This helps troubleshoot many issues */
                                    myExoPlayer.trackSelector?.parameters = builder?.clearOverridesOfType(C.TRACK_TYPE_TEXT)!!.build()
                                    lastSubtitleOverride = null

                                    /* Now, selecting our subtitle track should one be selected */
                                    lastSubtitleOverride = TrackSelectionOverride(
                                        media!!.subtitleTracks[index].trackGroup!!,
                                        media!!.subtitleTracks[index].index
                                    )
                                    myExoPlayer.trackSelector?.parameters = builder.addOverride(lastSubtitleOverride!!).build()
                                })

                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = (if (it.format?.label == null) {
                                        getString(R.string.room_track_track)
                                    } else {
                                        it.format?.label!!
                                    })
                                            + (it.format?.language).toString().uppercase(),
                                    color = Color.LightGray
                                )

                            }
                        }
                    }
                }

                FancyText(modifier = Modifier.constrainAs(audiocardsubtext) {
                    bottom.linkTo(parent.bottom, 14.dp)
                    absoluteLeft.linkTo(audiocard.absoluteLeft)
                    absoluteRight.linkTo(audiocard.absoluteRight)
                }, string = "Audio Tracks", solid = Color.Gray, size = 16f, font = Font(R.font.inter))

                FancyText(modifier = Modifier.constrainAs(subtitlecardsubtext) {
                    bottom.linkTo(parent.bottom, 14.dp)
                    absoluteLeft.linkTo(subtitlecard.absoluteLeft)
                    absoluteRight.linkTo(subtitlecard.absoluteRight)
                }, string = "Subtitle Tracks", solid = Color.Gray, size = 16f, font = Font(R.font.inter))

            }

        }

    }
}