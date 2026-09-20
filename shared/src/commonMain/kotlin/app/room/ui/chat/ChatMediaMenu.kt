package app.room.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import app.LocalRoomViewmodel
import app.i18n.strings
import app.klipy.KlipyFavorites
import app.theme.Radius
import app.theme.Space
import app.theme.palette
import app.uicomponents.chromeSurface
import app.uicomponents.controls.Icon
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.utils.platformCallback
import kotlinx.coroutines.launch

/**
 * The long-press menu of a GIF or sticker in chat: favourite it for the GIF panel, or copy its
 * link. It opens against the image it belongs to, so place it inside the image's box.
 */
@Composable
internal fun ChatMediaMenu(link: String, onDismiss: () -> Unit) {
    val p = palette
    val viewmodel = LocalRoomViewmodel.current
    val copied = strings.roomChatCopied
    val isFavorite = remember(link) { link in KlipyFavorites.links() }
    val gap = with(LocalDensity.current) { Space.gapTight.roundToPx() }

    Popup(
        popupPositionProvider = remember(gap) { BelowOrAbove(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 180.dp)
                .chromeSurface(Radius.panelShape)
                .padding(vertical = Space.gapTight),
        ) {
            ListRow(onClick = {
                onDismiss()
                // The room's scope, not this popup's: the popup leaves as the write starts.
                viewmodel.viewModelScope.launch {
                    if (isFavorite) KlipyFavorites.remove(link) else KlipyFavorites.add(KlipyFavorites.fromChatLink(link))
                }
            }) {
                Icon(if (isFavorite) Icons.Filled.HeartBroken else Icons.Filled.Favorite, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                RowGap()
                RowLabel(if (isFavorite) strings.roomGifActionUnfavorite else strings.roomGifActionFavorite)
            }
            ListRow(onClick = {
                onDismiss()
                platformCallback.copyText(link)
                viewmodel.dispatchOSD { copied }
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                RowGap()
                RowLabel(strings.roomChatCopyLink)
            }
        }
    }
}

/**
 * Under the anchor when the menu fits there, over it when it does not, and moved inside the window
 * sideways. Each bound is floored at zero, so a window smaller than the menu cannot make
 * `coerceIn` throw.
 */
internal class BelowOrAbove(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val x = start.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
