package app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
}
