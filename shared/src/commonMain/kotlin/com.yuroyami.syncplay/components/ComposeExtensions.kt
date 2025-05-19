package com.yuroyami.syncplay.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res

@Composable
fun getRegularFont() = Font(Res.font.Helvetica_Regular) //Helvetica

@Composable
fun getSyncplayFont() = Font(Res.font.Directive4_Regular)