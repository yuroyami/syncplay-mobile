package app.uicomponents.frames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.theme.Motion
import app.uicomponents.LocalWidthClass
import app.uicomponents.WidthClass
import app.theme.Radius
import app.theme.Space
import app.theme.Tier
import app.theme.Type
import app.theme.palette
import app.uicomponents.DialogBackdropBlur
import app.uicomponents.LocalInDialogWindow
import app.uicomponents.controls.CloseGlyph
import app.uicomponents.controls.GlyphButton
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SheetHandle
import app.uicomponents.glassScrim
import app.uicomponents.surface

/** The three modal sizes from DESIGN/POPUPS. On compact widths `Panel` and `Full` become sheets. */
enum class ModalSize { Ask, Panel, Full }

/**
 * The one modal frame. Owns the dialog window, the scrim, the entry, focus, Escape and back, and
 * dismissal; callers supply a title, a body and actions. The Android window blur is asked for from
 * inside this window, which is the only place it can be.
 */
@Composable
fun Modal(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    size: ModalSize = ModalSize.Panel,
    dismissable: Boolean = true,
    inset: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    if (!open) return
    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = dismissable,
        ),
    ) {
        DialogBackdropBlur()
        CompositionLocalProvider(LocalInDialogWindow provides true) {
            ModalFrame(size, title, dismissable, onDismiss, actions, inset, body)
        }
    }
}

/** The frame without its dialog window, so the render harness can draw it. */
@Composable
internal fun ModalFrame(
    size: ModalSize,
    title: String?,
    dismissable: Boolean,
    onDismiss: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
    inset: Boolean = true,
    body: @Composable ColumnScope.() -> Unit,
) {
    val p = palette
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val windowWidth = with(density) { window.width.toDp() }
    val windowHeight = with(density) { window.height.toDp() }
    val sheet = size != ModalSize.Ask && LocalWidthClass.current == WidthClass.Compact
    val visible = remember { MutableTransitionState(false) }.apply { targetState = true }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(glassScrim)
            .imePadding()
            .then(
                if (dismissable) Modifier.clickable(interactionSource = null, indication = null) { onDismiss() }
                else Modifier
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (dismissable && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss(); true
                } else false
            },
        contentAlignment = if (sheet) Alignment.BottomCenter else Alignment.Center,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = if (sheet) slideInVertically(Motion.move()) { it } + fadeIn(Motion.quick()) else fadeIn(Motion.quick()),
            exit = if (sheet) slideOutVertically(Motion.move()) { it } + fadeOut(Motion.quick()) else fadeOut(Motion.quick()),
        ) {
            val shape = if (sheet) RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel) else Radius.panelShape
            Column(
                modifier = Modifier
                    .then(
                        when {
                            sheet -> Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.88f)
                            size == ModalSize.Ask -> Modifier.fillMaxWidth(0.88f).widthIn(max = 320.dp).heightIn(max = windowHeight * 0.88f)
                            size == ModalSize.Panel -> Modifier.fillMaxWidth(0.92f).widthIn(max = 440.dp).heightIn(max = windowHeight * 0.88f)
                            else -> Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f)
                        }
                    )
                    .surface(Tier.Panel, shape)
                    .clickable(interactionSource = null, indication = null) { /* consume, so a tap inside never dismisses */ },
            ) {
                if (sheet) SheetHandle()
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(Space.row).padding(start = Space.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = Type.label, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (dismissable) GlyphButton(CloseGlyph, name = "Close", onClick = onDismiss, tint = p.inkDim)
                    }
                    Rule()
                }
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = size == ModalSize.Full)
                        .verticalScroll(scroll)
                        .then(if (inset) Modifier.padding(horizontal = Space.gutter, vertical = Space.gap) else Modifier.padding(vertical = Space.gapTight)),
                    content = body,
                )
                if (actions != null) {
                    Rule()
                    Row(
                        modifier = Modifier.fillMaxWidth().height(Space.rowTall).padding(horizontal = Space.gap),
                        horizontalArrangement = Arrangement.spacedBy(Space.gapTight, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }
    }
}

/** A hairline that appears under a scrolled header; kept here so every frame draws it the same. */
@Composable
internal fun ScrolledRule(scrolled: Boolean) {
    Rule(Modifier.alpha(if (scrolled) 1f else 0f))
}
