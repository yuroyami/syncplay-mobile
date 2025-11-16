package com.yuroyami.syncplay.ui.theme

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.WebStories
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.kborowy.colorpicker.KolorPicker
import com.yuroyami.syncplay.ui.components.FlexibleText
import com.yuroyami.syncplay.ui.components.jostFont
import com.yuroyami.syncplay.ui.components.lexendFont
import com.yuroyami.syncplay.ui.screens.Screen
import com.yuroyami.syncplay.ui.screens.adam.LocalGlobalViewmodel
import com.yuroyami.syncplay.ui.screens.home.HomeLeadingTitle
import com.yuroyami.syncplay.ui.screens.home.HomeTextField
import com.yuroyami.syncplay.ui.screens.room.tabs.RoomTab
import com.yuroyami.syncplay.ui.theme.SaveableTheme.Companion.toTheme


@Composable
fun ThemeCreatorScreenUI() {
    val globalViewmodel = LocalGlobalViewmodel.current

    var newTheme by remember { mutableStateOf<SaveableTheme>(globalViewmodel.themeManager.currentTheme.value.toTheme()) }
    val dynamicScheme by derivedStateOf { newTheme.dynamicScheme }

    MaterialTheme(
        colorScheme = dynamicScheme
    ) {
        Scaffold(
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
                                globalViewmodel.backstack.let { stack ->
                                    if (stack.contains(Screen.ThemeCreator)) {
                                        stack.remove(Screen.ThemeCreator)
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {

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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FlexibleText(
                        text = "Theme name", //TODO Localize
                        size = 14f,
                        textAlign = TextAlign.Center,
                        fillingColors = listOf(MaterialTheme.colorScheme.primary),
                        font = lexendFont,
                        strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
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
                        }
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
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
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
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
                        shadowSize = 3f
                    )

                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                    ThemeSingleColorPicker(
                        initialColor = newTheme.secondaryColor?.let { Color(it) },
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
                        text = "Tertiary color", //TODO Localize
                        size = 14f,
                        textAlign = TextAlign.Center,
                        fillingColors = listOf(MaterialTheme.colorScheme.primary),
                        font = lexendFont,
                        strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
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
                        text = "is a Dark Theme", //TODO Localize
                        size = 14f,
                        textAlign = TextAlign.Center,
                        fillingColors = listOf(MaterialTheme.colorScheme.primary),
                        font = lexendFont,
                        strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
                        shadowSize = 3f
                    )

                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                    Checkbox(
                        checked = true,
                        onCheckedChange = {}
                    )
                }

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
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
                        shadowSize = 3f
                    )

                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                    Checkbox(
                        checked = true,
                        onCheckedChange = {}
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
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
                        shadowSize = 3f
                    )

                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                    Checkbox(
                        checked = true,
                        onCheckedChange = {}
                    )
                }


                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FlexibleText(
                        text = "Palette Style", //TODO Localize
                        size = 14f,
                        textAlign = TextAlign.Center,
                        fillingColors = listOf(MaterialTheme.colorScheme.primary),
                        font = lexendFont,
                        strokeColors = listOf(MaterialTheme.colorScheme.scrim),
                        shadowColors = Theming.SP_GRADIENT.map { it.copy(alpha = 0.65f) },
                        shadowSize = 3f
                    )

                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 4.dp).alpha(0.5f))

                    Checkbox(
                        checked = true,
                        onCheckedChange = {}
                    )
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

@Composable
fun ThemeSingleColorPicker(initialColor: Color? = null, onColorChange: (Color) -> Unit)  {
    var pickerVisible by remember { mutableStateOf(false) }

    Surface(
        color = initialColor ?: Color.Transparent,
        modifier = Modifier.height(42.dp).width(96.dp).padding(2.dp),
        shape = RoundedCornerShape(6.dp),
        border = if (initialColor == null) BorderStroke(width = Dp.Hairline, Color.Gray) else null,
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
            modifier = Modifier.fillMaxWidth(0.8f).weight(3f).padding(6.dp),
            initialColor = initialColor ?: Color.Black,
            onColorSelected = onColorChange
        )
    }
}