package app.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import SyncplayMobile.shared.KiteBuildConfig
import app.LocalTheme

object Theming {

    /** The three seed colors of the active theme, as stored, in order. A missing seed falls back
     * to the brand color. The logo and the wordmark draw from this list, and [Palette.brandField]
     * holds the same seeds for the join button, the TV focus ring and the seekbar fill. These are
     * the raw seeds, not scheme colors: MaterialKolor mutes seeds into softer scheme colors, and
     * the brand surfaces need them vivid. */
    val flexibleGradient: List<Color>
        @Composable get() = LocalTheme.current.let {
            listOf(
                it.primaryColor?.let(::Color) ?: NeoSP1,
                it.secondaryColor?.let(::Color) ?: NeoSP2,
                it.tertiaryColor?.let(::Color) ?: NeoSP3,
            )
        }

    /* The brand gradient. AppConfig.TRINITY_* in buildSrc sets these three colors. */
    val NeoSP1 = Color(KiteBuildConfig.TRINITY_COLOR_1)
    val NeoSP2 = Color(KiteBuildConfig.TRINITY_COLOR_2)
    val NeoSP3 = Color(KiteBuildConfig.TRINITY_COLOR_3)
    val SP_GRADIENT = listOf(NeoSP1, NeoSP2, NeoSP3)
}
