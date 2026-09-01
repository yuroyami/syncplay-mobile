package app.uicomponents

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Lexend_variable
import syncplaymobile.shared.generated.resources.Res

/** The one text face; every type role reads it. */
val lexendFont: Font
    @Composable get() = Font(Res.font.Lexend_variable)

/** The wordmark face, for the brand name only. */
val syncplayFont: Font
    @Composable get() = Font(Res.font.Directive4_Regular)
