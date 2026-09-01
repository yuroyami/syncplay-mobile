package app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import SyncplayMobile.shared.KiteBuildConfig
import app.LocalTheme

object Theming {

    /** The active theme's trinity: its three RAW seed colors, in declared order. Every identity
     * moment (logo, wordmark, join button, TV focus ring, seekbar fill) draws from this, so the
     * theme and the brand always agree. Raw seeds, not scheme roles, because MaterialKolor's
     * tonal mapping mutes seeds into chrome colors and the identity moments want them vivid. */
    val flexibleGradient: List<Color>
        @Composable get() = LocalTheme.current.let {
            listOf(
                it.primaryColor?.let(::Color) ?: NeoSP1,
                it.secondaryColor?.let(::Color) ?: NeoSP2,
                it.tertiaryColor?.let(::Color) ?: NeoSP3,
            )
        }

    /* ── Brand gradient (SSOT: AppConfig.TRINITY_*) ────────────────── */
    val NeoSP1 = Color(KiteBuildConfig.TRINITY_COLOR_1)
    val NeoSP2 = Color(KiteBuildConfig.TRINITY_COLOR_2)
    val NeoSP3 = Color(KiteBuildConfig.TRINITY_COLOR_3)
    val SP_GRADIENT = listOf(NeoSP1, NeoSP2, NeoSP3)

    /* ── Semantic: chat message color defaults (user-overridable prefs) ── */
    val MSG_SELF_TAG = Color(0xFFFF5A5A)
    val MSG_FRIEND_TAG = Color(0xFF7FA7E8)
    val MSG_SYSTEM = Color(0xFFE6E6E6)
    val MSG_ERROR = Color(0xFFFF6E6E)
    val MSG_CHAT = Color.White
    val MSG_TIMESTAMP = Color(0xFFFF5F87)

    /* ── Semantic: readiness indicators ─────────────────────────────── */
    val READY_GREEN = Color(0xFF6ECB5A)
    val UNREADY_RED = Color(0xFFE85455)

    /* ── Spacing scale ──────────────────────────────────────────────── */
    val SpaceXS = 4.dp
    val SpaceSM = 8.dp
    val SpaceMD = 12.dp
    val SpaceLG = 16.dp
    val SpaceXL = 24.dp

    /* ── Dimensions ─────────────────────────────────────────────────── */
    const val ROOM_ICON_SIZE = 38
    const val USER_INFO_IC_SIZE = 16
    const val USER_INFO_TXT_SIZE = 10

    /* ── Derived Material backgrounds ──────────────────────────────── */
    val backgroundGradient: List<Color>
        @Composable get() = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background
        )

}
