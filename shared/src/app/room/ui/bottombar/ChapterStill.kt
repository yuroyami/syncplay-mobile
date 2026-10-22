package app.room.ui.bottombar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import app.player.ChapterStills
import app.player.models.Chapter
import app.player.models.MediaFile
import app.theme.Radius
import app.theme.palette

/**
 * A chapter's still frame in a 16:9 box. The frame loads once the box is on screen, so a long
 * chapter list never decodes every chapter at once. The box stays empty while the frame loads,
 * and when none can be taken.
 */
@Composable
internal fun ChapterStill(stills: ChapterStills, media: MediaFile, chapter: Chapter, chapterEndMs: Long?, modifier: Modifier = Modifier) {
    var onScreen by remember(chapter) { mutableStateOf(false) }
    var still by remember(media.location, chapter) { mutableStateOf<ImageBitmap?>(null) }
    if (onScreen) {
        LaunchedEffect(media.location, chapter) { still = stills.still(media, chapter, chapterEndMs) }
    }
    Box(
        modifier
            .size(64.dp, 36.dp)
            .clip(Radius.tightShape)
            .background(palette.rule)
            // A row scrolled out of the list has no visible bounds, so it waits.
            .onGloballyPositioned { if (!onScreen && it.boundsInWindow().height > 0f) onScreen = true },
    ) {
        still?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
    }
}
