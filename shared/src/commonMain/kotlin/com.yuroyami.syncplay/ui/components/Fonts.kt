package com.yuroyami.syncplay.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Helvetica_Regular
import syncplaymobile.shared.generated.resources.Jost_variable
import syncplaymobile.shared.generated.resources.Lexend_variable
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.Saira_variable

val lexendFont: Font
    @Composable get() = Font(Res.font.Lexend_variable)

val jostFont: Font
    @Composable get() = Font(Res.font.Jost_variable)

val sairaFont: Font
    @Composable get() = Font(Res.font.Saira_variable)

val helveticaFont: Font
    @Composable get() = Font(Res.font.Helvetica_Regular) //Helvetica

val syncplayFont: Font
    @Composable get() = Font(Res.font.Directive4_Regular)