package com.yuroyami.syncplay.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Res

@Composable
fun getRegularFont() = Font(Res.font.Helvetica_Regular) //Helvetica

val syncplayFont: Font
    @Composable get() = Font(Res.font.Directive4_Regular)