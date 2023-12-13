package com.yuroyami.syncplay.compose

import SyncplayMobile.generated.resources.Res
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import org.jetbrains.compose.resources.readResourceBytes

internal lateinit var d4: Font
internal lateinit var inter: Font

@Composable
fun fontDirective() = if (::d4.isInitialized) d4 else {
    org.jetbrains.compose.resources.Font(Res.fonts.`directive4-regular`)
    //MR.fonts.Directive4.regular.asFont()!!.also { d4 = it }
}

@Composable
fun fontInter() = if (::inter.isInitialized) inter else  {
    org.jetbrains.compose.resources.Font(Res.fonts.`inter-regular`)
    //MR.fonts.Inter.regular.asFont()!!.also { inter = it }
}

suspend fun daynightAsset() = readResourceBytes("assets/daynight_toggle.json").decodeToString()