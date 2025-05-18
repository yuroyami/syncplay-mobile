package com.yuroyami.syncplay.compose

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.Res.readBytes

suspend fun daynightAsset() = readBytes("files/daynight_toggle.json").decodeToString()

@Composable
fun getRegularFont() = Font(Res.font.Helvetica_Regular) //Helvetica

@Composable
fun getSyncplayFont() = Font(Res.font.Directive4_Regular)