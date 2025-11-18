package com.yuroyami.syncplay.ui.screens.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.WebStories
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Slider
import com.composeunstyled.rememberSliderState
import com.kborowy.colorpicker.KolorPicker
import com.materialkolor.PaletteStyle
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.jostFont
import com.yuroyami.syncplay.ui.components.lexendFont
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.ui.screens.adam.LocalTheme
import com.yuroyami.syncplay.ui.screens.home.HomeLeadingTitle
import com.yuroyami.syncplay.ui.screens.home.HomeTextField
import com.yuroyami.syncplay.ui.screens.room.tabs.RoomTab
import com.yuroyami.syncplay.utils.loggy
import kotlinx.coroutines.launch

@Composable
fun ThemeCreatorScreenUI(themeToEdit: SaveableTheme? = null) {
    val globalViewmodel = LocalGlobalViewmodel.current

    var newTheme by remember { mutableStateOf(themeToEdit ?: globalViewmodel.themeManager.currentTheme.value) }

    LaunchedEffect(newTheme) {
        loggy(newTheme.toString())
    }

    val dynamicScheme by derivedStateOf { newTheme.dynamicScheme }
    val useSPGrad by derivedStateOf { newTheme.syncplayGradients }

    val snackState = remember { SnackbarHostState() }

    CompositionLocalProvider(
        LocalTheme provides newTheme
    ) {
        MaterialTheme(
            colorScheme = dynamicScheme
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        title = {
                            FlexibleText(
                                text = "Theme Customization", //TODO Localize
                                size = 18f,
                                textAlign = TextAlign.Center,
                                fillingColors = listOf(MaterialTheme.colorScheme.primary),
                                font = jostFont,
                                strokeColors = listOf(MaterialTheme.colorScheme.scrim)
                            )
                        },
                        actions = {
                            Button(
                                onClick = {
                                    globalViewmodel.backstack.removeLast()
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    globalViewmodel.viewModelScope.launch {
                                        if (themeToEdit != null) {
                                            globalViewmodel.themeManager.deleteTheme(themeToEdit)
                                        }
                                        val isSaved = globalViewmodel.themeManager.saveNewTheme(newTheme)

                                        if (isSaved) {
                                            globalViewmodel.backstack.removeLast()
                                        } else {
                                            snackState.showSnackbar(
                                                "Theme already exists!" //TODO
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text("Save") //TODO
                            }
                        }
                    )
                }
            ) { paddings ->
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.padding(paddings).padding(8.dp).verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        FlexibleText(
                            text = "Theme name", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        HomeTextField(
                            modifier = Modifier.width(200.dp),
                            label = "Theme Name",
                            value = newTheme.name,
                            onValueChange = {
                                newTheme = newTheme.copy(
                                    name = it
                                )
                            },
                            height = 48.dp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Primary color", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        ThemeSingleColorPicker(
                            initialColor = Color(newTheme.primaryColor),
                            onColorChange = { color ->
                                newTheme = newTheme.copy(
                                    primaryColor = color.toArgb()
                                )
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Secondary color", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        ThemeSingleColorPicker(
                            initialColor = newTheme.secondaryColor?.let { Color(it) },
                            onColorChange = { color ->
                                newTheme = newTheme.copy(
                                    secondaryColor = color.toArgb()
                                )
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Tertiary color", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        ThemeSingleColorPicker(
                            initialColor = newTheme.tertiaryColor?.let { Color(it) },
                            onColorChange = { color ->
                                newTheme = newTheme.copy(
                                    tertiaryColor = color.toArgb()
                                )
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Neutral color", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        ThemeSingleColorPicker(
                            initialColor = newTheme.neutralColor?.let { Color(it) },
                            onColorChange = { color ->
                                newTheme = newTheme.copy(
                                    neutralColor = color.toArgb()
                                )
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Neutral Variant color", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        ThemeSingleColorPicker(
                            initialColor = newTheme.neutralVariantColor?.let { Color(it) },
                            onColorChange = { color ->
                                newTheme = newTheme.copy(
                                    neutralVariantColor = color.toArgb()
                                )
                            }
                        )
                    }

//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        modifier = Modifier.fillMaxWidth(),
//                    ) {
//                        FlexibleText(
//                            text = "is a Dark Theme", //TODO Localize
//                            size = 14f,
//                            textAlign = TextAlign.Center,
//                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
//                            font = lexendFont,
//                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
//                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
//                            shadowSize = 3f
//                        )
//
//                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))
//
//                        Checkbox(
//                            checked = newTheme.isDark,
//                            onCheckedChange = { newTheme = newTheme.copy(isDark = it) }
//                        )
//                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "is an AMOLED Theme", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        Checkbox(
                            checked = newTheme.isAMOLED,
                            onCheckedChange = { newTheme = newTheme.copy(isAMOLED = it) }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Use Syncplay gradients", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        Checkbox(
                            checked = newTheme.syncplayGradients,
                            onCheckedChange = { newTheme = newTheme.copy(syncplayGradients = it) }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FlexibleText(
                            text = "Contrast", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        val sliderState = rememberSliderState(initialValue = 0.0f, valueRange = (-1.0f..1.0f))

                        LaunchedEffect(sliderState.value) {
                            newTheme = newTheme.copy(
                                contrast = sliderState.value.toDouble()
                            )
                        }

                        Slider(
                            state = sliderState,
                            track = {
                                Box(
                                    modifier = Modifier
                                        .width(150.dp)
                                        .height(4.dp)
                                        .background(Color.Gray)
                                )
                            },
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        val paletteSelector = remember { mutableStateOf(false) }

                        FlexibleText(
                            text = "Palette Style", //TODO Localize
                            size = 14f,
                            textAlign = TextAlign.Center,
                            fillingColors = listOf(MaterialTheme.colorScheme.primary),
                            font = lexendFont,
                            strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                            shadowColors = if (useSPGrad) Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) } else listOf(),
                            shadowSize = 3f
                        )

                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                        ExposedDropdownMenuBox(
                            expanded = paletteSelector.value,
                            onExpandedChange = {
                                paletteSelector.value = !paletteSelector.value
                            }
                        ) {
                            HomeTextField(
                                modifier = Modifier.fillMaxWidth(0.75f)
                                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                icon = Icons.Outlined.Palette,
                                value = newTheme.style.name,
                                dropdownState = paletteSelector,
                                onValueChange = {},
                                height = 48.dp
                            )

                            ExposedDropdownMenu(
                                modifier = Modifier.background(color = MaterialTheme.colorScheme.tertiaryContainer),
                                expanded = paletteSelector.value,
                                onDismissRequest = {
                                    paletteSelector.value = false
                                }
                            ) {
                                PaletteStyle.entries.forEach { paletteStyle ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(paletteStyle.name, color = Color.White)
                                        },
                                        onClick = {
                                            paletteSelector.value = false

                                            newTheme = newTheme.copy(style = paletteStyle)
                                        }
                                    )
                                }
                            }
                        }
                    }


                    HorizontalDivider()

                    /********$ PREVIEWS $*********/

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        HomeLeadingTitle(
                            string = "Preview"
                        )

                        HomeTextField(
                            modifier = Modifier.width(200.dp),
                            icon = Icons.Filled.WebStories,
                            label = "Preview",
                            value = "Preview",
                            dropdownState = null,
                            onValueChange = { },
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        SplitButtonDefaults.LeadingButton(
                            contentPadding = PaddingValues(vertical = 16.dp),
                            modifier = Modifier.width(200.dp),
                            onClick = {
                            },
                            content = {
                                Icon(imageVector = Icons.Filled.WebStories, "")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Preview", fontSize = 18.sp)
                            }
                        )

                        RoomTab(
                            modifier = Modifier.size(46.dp),
                            icon = Icons.Filled.WebStories,
                            visibilityState = false,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSingleColorPicker(initialColor: Color? = null, onColorChange: (Color) -> Unit) {
    var pickerVisible by remember { mutableStateOf(false) }

    Box {
        Surface(
            color = initialColor ?: Color.Transparent,
            modifier = Modifier.height(42.dp).width(96.dp).padding(2.dp),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(width = Dp.Hairline, Color.Gray),
            onClick = {
                pickerVisible = !pickerVisible
            }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (initialColor == null) {
                    Icon(Icons.Filled.MotionPhotosOff, null, tint = Color.Gray)
                }
            }
        }

        DropdownMenu(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
            shape = RoundedCornerShape(8.dp),
            expanded = pickerVisible,
            properties = PopupProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
            onDismissRequest = {
                pickerVisible = false
            }
        ) {
            /* The card that holds the color picker */
            KolorPicker(
                modifier = Modifier.width(200.dp).height(165.dp).padding(6.dp),
                initialColor = initialColor ?: Color(22, 22, 22),
                onColorSelected = { color ->
                    if (color != Color(22, 22, 22)) {
                        onColorChange(color)
                    }
                }
            )
        }
    }
}