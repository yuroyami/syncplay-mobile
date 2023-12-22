package com.yuroyami.syncplay.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import org.jetbrains.compose.resources.readResourceBytes
import syncplaymobile.generated.resources.Res

internal lateinit var d4: Font
internal lateinit var inter: Font

@Composable
fun fontDirective() = if (::d4.isInitialized) d4 else {
    org.jetbrains.compose.resources.Font(Res.font.directive4_regular)
    //MR.fonts.Directive4.regular.asFont()!!.also { d4 = it }
}

@Composable
fun fontInter() = if (::inter.isInitialized) inter else  {
    org.jetbrains.compose.resources.Font(Res.font.inter_regular)
    //MR.fonts.Inter.regular.asFont()!!.also { inter = it }
}

suspend fun daynightAsset() = readResourceBytes("assets/daynight_toggle.json").decodeToString()