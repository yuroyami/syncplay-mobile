package app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.LocalTheme

object Theming {

    val useSyncplayGradient: Boolean
        @Composable get() = LocalTheme.current.syncplayGradients

    val flexibleGradient: List<Color>
        @Composable get() {
            val fallback = MaterialTheme.colorScheme.primary
            return if (useSyncplayGradient) SP_GRADIENT else listOf(fallback, fallback)
        }

    /* ── Brand gradient ─────────────────────────────────────────────── */
    val NeoSP1 = Color(0xFF4FD1FF)
    val NeoSP2 = Color(0xFF5A7CFF)
    val NeoSP3 = Color(0xFF7A3CFF)
    val SP_GRADIENT = listOf(NeoSP1, NeoSP2, NeoSP3)

    /* ── Semantic: chat message colors ──────────────────────────────── */
    val MSG_SELF_TAG = Color(204, 36, 36, 255)
    val MSG_FRIEND_TAG = Color(96, 130, 182)
    val MSG_SYSTEM = Color(230, 230, 230)
    val MSG_ERROR = Color(150, 20, 20, 255)
    val MSG_CHAT = Color.White
    val MSG_TIMESTAMP = Color(255, 95, 135)

    /* ── Semantic: readiness indicators ─────────────────────────────── */
    val READY_GREEN = Color(0xFF6ECB5A)
    val UNREADY_RED = Color(0xFFE85455)

    /* ── Spacing tokens ─────────────────────────────────────────────── */
    const val SPACING_XS = 2
    const val SPACING_SM = 4
    const val SPACING_MD = 8
    const val SPACING_LG = 16
    const val SPACING_XL = 24

    /* ── Dimensions ─────────────────────────────────────────────────── */
    const val ROOM_ICON_SIZE = 38
    const val USER_INFO_IC_SIZE = 16
    const val USER_INFO_TXT_SIZE = 10

    /* ── Legacy aliases (used outside room UI — migrate gradually) ── */
    val SP_YELLOW = Color(255, 198, 4)
    val SP_ORANGE = Color(255, 113, 54)
    val SP_PINK = Color(255, 40, 97)
    val SP_PALE = Color(255, 233, 166)
    val SP_INTENSE_PINK = Color(255, 21, 48)
    val OLD_SP_YELLOW = Color(255, 214, 111)
    val OLD_SP_PINK = Color(239, 100, 147)
    val ROOM_USER_READY_ICON = READY_GREEN
    val ROOM_USER_UNREADY_ICON = UNREADY_RED

    val SHADER_GRADIENT = listOf(
        Color.Transparent,
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color(0, 0, 0, 100),
        Color.Transparent
    )

    /* ── Derived Material backgrounds ──────────────────────────────── */
    val backgroundGradient: List<Color>
        @Composable get() = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )

}