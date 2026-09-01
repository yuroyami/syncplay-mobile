package app.uicomponents

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import app.theme.Theming

/**
 * The app mark, drawn live so the active theme's trinity flows through it. The static drawable
 * could only ever show the colors it was exported with; this draws the same silhouette and
 * highlight, with the sail gradient built from [Theming.flexibleGradient] at composition time.
 * Theme changes recolor the logo, the wordmark and every accent in the same frame.
 */
@Composable
fun SynkplayLogo(modifier: Modifier) {
    val trinity = Theming.flexibleGradient
    val path = remember {
        PathParser().parsePathString(LOGO_PATH).toPath().apply { fillType = PathFillType.EvenOdd }
    }

    Canvas(modifier) {
        val s = size.minDimension / VIEWPORT
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            // Same geometry as the shipped art: a dark lead-in, then the trinity, with the last
            // color held to the tip. The lead-in mixes the first two seeds and darkens them; both
            // constants are tuned (in Oklab, which is what Compose's lerp uses) so the brand
            // trinity reproduces the artwork's #793695 anchor. Custom themes get the same recipe.
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    0f to lerp(lerp(trinity[0], trinity[1], 0.63f), Color.Black, 0.26f),
                    0.25f to trinity[0],
                    0.55f to trinity[1],
                    0.88f to trinity[2],
                    1f to trinity[2],
                    start = Offset(250f, 1200f),
                    end = Offset(1160f, 100f),
                )
            )
            // Fixed near-white sheen over the wing shoulder, theme-independent.
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    0f to Color(0x57F3ECFF),
                    0.42f to Color(0x24F3ECFF),
                    1f to Color(0x00F3ECFF),
                    center = Offset(440f, 527f),
                    radius = 602f,
                )
            )
        }
    }
}

private const val VIEWPORT = 1254f

private const val LOGO_PATH = "M 1187 102.5 Q 1195 115 1194 144.5 Q 1193 174 1188 188 Q 1183 202 1158.5 242.5 Q 1134 283 1089.5 321 Q 1045 359 1015.5 377 Q 986 395 927.5 417 Q 869 439 860 450.5 Q 851 462 854.5 473 Q 858 484 869 486.5 Q 880 489 957 456.5 Q 1034 424 1093 384.5 Q 1152 345 1167.5 354.5 Q 1183 364 1179.5 399.5 Q 1176 435 1155 476 Q 1134 517 1113 544 Q 1092 571 1060.5 595 Q 1029 619 990 639 Q 951 659 917 667 Q 883 675 872.5 686 Q 862 697 863.5 707.5 Q 865 718 877.5 722.5 Q 890 727 937.5 711 Q 985 695 1027.5 669.5 Q 1070 644 1089 645.5 Q 1108 647 1108.5 662 Q 1109 677 1099.5 706 Q 1090 735 1068 767.5 Q 1046 800 1002.5 834.5 Q 959 869 905.5 890.5 Q 852 912 787 980 Q 722 1048 661.5 1089 Q 601 1130 544.5 1157 Q 488 1184 443.5 1197 Q 399 1210 368 1212 Q 337 1214 322 1208.5 Q 307 1203 302.5 1191.5 Q 298 1180 318.5 1147.5 Q 339 1115 339 1088 Q 339 1061 280 1001 Q 221 941 200 908 Q 179 875 161 814.5 Q 143 754 107.5 725 Q 72 696 64.5 680.5 Q 57 665 54 648 Q 51 631 55.5 609.5 Q 60 588 90 539.5 Q 120 491 168.5 451.5 Q 217 412 294 375.5 Q 371 339 617.5 269 Q 864 199 1007 144 Q 1150 89 1164.5 89.5 Q 1179 90 1187 102.5 Z M 797 750.5 Q 801 701 782.5 643.5 Q 764 586 730 546 Q 696 506 654.5 483.5 Q 613 461 558.5 455 Q 504 449 459.5 461 Q 415 473 382 494 Q 349 515 333.5 530.5 Q 318 546 306.5 561.5 Q 295 577 278 616.5 Q 261 656 257 708 Q 253 760 268.5 810.5 Q 284 861 318.5 904 Q 353 947 384 966.5 Q 415 986 437 994 Q 459 1002 510.5 1005.5 Q 562 1009 610 993.5 Q 658 978 699 943 Q 740 908 766.5 854 Q 793 800 797 750.5 Z M 628 524.5 Q 669 545 695 576.5 Q 721 608 735 641.5 Q 749 675 748 730 Q 747 785 731 821 Q 715 857 689.5 884.5 Q 664 912 625.5 933.5 Q 587 955 565.5 959 Q 544 963 516 959.5 Q 488 956 443.5 937.5 Q 399 919 375.5 896 Q 352 873 330.5 835.5 Q 309 798 308 740.5 Q 307 683 324 639.5 Q 341 596 383.5 556.5 Q 426 517 452.5 510 Q 479 503 533 503.5 Q 587 504 628 524.5 Z M 460 584 Q 447 581 437.5 592 Q 428 603 428 717.5 Q 428 832 431 850 Q 434 868 447 870.5 Q 460 873 560.5 812.5 Q 661 752 667 738.5 Q 673 725 644.5 701 Q 616 677 544.5 632 Q 473 587 460 584 Z"
