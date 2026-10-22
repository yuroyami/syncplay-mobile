package app.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.uicomponents.controls.ScrubTrack
import kotlin.test.Test

class ScrubTrackLayoutTest {
    @Test
    fun narrowAndZeroWidthTracksRenderInBothDirections() {
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            DesignHarness.render("scrub-narrow-$direction", widthDp = 80, heightDp = 800) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Column {
                        for (width in listOf(0, 1, 2, 3, 8)) {
                            for (value in listOf(0f, 0.5f, 1f)) {
                                Box(Modifier.requiredWidth(width.dp)) {
                                    ScrubTrack(
                                        value = value,
                                        onValueChange = {},
                                        buffered = 0.75f,
                                        ticks = listOf(0f, 0.5f, 1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
