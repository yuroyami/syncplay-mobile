package com.yuroyami.syncplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuroyami.syncplay.managers.datastore.DataStoreKeys
import com.yuroyami.syncplay.managers.datastore.valueAsState
import com.yuroyami.syncplay.models.MessagePalette
import com.yuroyami.syncplay.ui.theme.Theming
import com.yuroyami.syncplay.ui.theme.Theming.backgroundGradient
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res

val messagePalette: State<MessagePalette>
    @Composable get() {
        val colorTimestamp = DataStoreKeys.PREF_INROOM_COLOR_TIMESTAMP.valueAsState(Theming.MSG_TIMESTAMP.toArgb())
        val colorSelftag = DataStoreKeys.PREF_INROOM_COLOR_SELFTAG.valueAsState(Theming.MSG_SELF_TAG.toArgb())
        val colorFriendtag = DataStoreKeys.PREF_INROOM_COLOR_FRIENDTAG.valueAsState(Theming.MSG_FRIEND_TAG.toArgb())
        val colorSystem = DataStoreKeys.PREF_INROOM_COLOR_SYSTEMMSG.valueAsState(Theming.MSG_SYSTEM.toArgb())
        val colorUserchat = DataStoreKeys.PREF_INROOM_COLOR_USERMSG.valueAsState(Theming.MSG_CHAT.toArgb())
        val colorError = DataStoreKeys.PREF_INROOM_COLOR_ERRORMSG.valueAsState(Theming.MSG_ERROR.toArgb())
        val msgIncludeTimestamp = DataStoreKeys.PREF_INROOM_MSG_ACTIVATE_STAMP.valueAsState(true)

        return derivedStateOf {
            MessagePalette(
                timestampColor = Color(colorTimestamp.value),
                selftagColor = Color(colorSelftag.value),
                friendtagColor = Color(colorFriendtag.value),
                systemmsgColor = Color(colorSystem.value),
                usermsgColor = Color(colorUserchat.value),
                errormsgColor = Color(colorError.value),
                includeTimestamp = msgIncludeTimestamp.value
            )
        }
    }