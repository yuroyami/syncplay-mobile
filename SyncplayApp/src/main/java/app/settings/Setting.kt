package app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.R
import app.datastore.DataStoreUtils
import app.datastore.DataStoreUtils.booleanFlow
import app.datastore.DataStoreUtils.intFlow
import app.datastore.DataStoreUtils.stringFlow
import app.datastore.DataStoreUtils.writeBoolean
import app.datastore.DataStoreUtils.writeInt
import app.datastore.DataStoreUtils.writeString
import app.ui.Paletting
import app.ui.compose.ComposeUtils.FancyIcon
import app.ui.compose.ComposeUtils.MultiChoiceDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalTextApi::class)
/** Main class that does the required logic and UI for a single Setting.
 * @param type The type of the setting, must be a [SettingType]
 * @param key Key string that identifies the setting, to save it and read it.
 * @param title Title of the setting, visible to the user, which appears in a bigger text size.
 * @param summary Summary of the setting, preferably explaining how the setting works.
 * @param icon Icon for the setting, it's null by default so no icon will be shown unless you pass an [ImageVector] for it
 * @param defaultValue The default value of the setting, can be of any type but will throw an exception when incompatible
 * @param enabled Whether the setting is enabled, this is true by default if you don't pass any value so it's optional.
 * @param dependency Key string of another setting that this setting is dependent upon (Optional)
 * @param maxValue Max value of the setting if it works with numbers, 100 by default, this setting is optional.
 * @param minValue Min value of the setting, 0 by default, so this setting is optional.
 * @param entryKeys When working with multi choice setting for example, pass a list of the visible key texts
 * @param entryValues The under-the-hood values for each entryKey. It must be of same size as entryKeys.
 * @param onClick Lambda to be triggered upon clicking the setting, typically used for [SettingType.OneClickSetting]
 * @param onItemChosen Lambda to be triggered upon choosing an elemnt for a [SettingType.MultiChoicePopupSetting]*/
class Setting(
    val datastorekey: String,
    val type: SettingType, val key: String, val title: String, val summary: String,
    val defaultValue: Any? = null, val icon: ImageVector? = null, val enabled: Boolean = true,
    val dependency: String = "",
    val maxValue: Int = 100, val minValue: Int = 0,
    val entryKeys: List<String> = listOf(), val entryValues: List<String> = listOf(),
    val onClick: (() -> Unit)? = null, val onItemChosen: ((index: Int, value: String) -> Unit)? = null,
) {

    private val pdng = 12
    private val icsize = 28

    /** The Composable function that creates the UI element for the setting. */
    @Composable
    fun SettingSingleton(modifier: Modifier = Modifier) {
        when (type) {
            SettingType.OneClickSetting -> OneClickSettingUI(modifier)
            SettingType.MultiChoicePopupSetting -> ListSettingUI(modifier)
            SettingType.CheckboxSetting -> BooleanSettingUI(modifier = modifier, true)
            SettingType.ToggleSetting -> BooleanSettingUI(modifier = modifier, false)
            SettingType.SliderSetting -> SliderSettingUI(modifier = modifier)
        }
    }

    @Composable
    private fun OneClickSettingUI(modifier: Modifier = Modifier) {
        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    onClick?.let { it() }
                }
        ) {
            val (ic, ttl, smry, spacer) = createRefs()

            FancyIcon(icon = icon, size = icsize, modifier = Modifier.constrainAs(ic) {
                top.linkTo(parent.top, pdng.dp)
                start.linkTo(parent.start, pdng.dp)
            })

            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, pdng.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                    shadow = Shadow(offset = Offset(0f, 0f), blurRadius = 1f),
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (15.5).sp
                )
            )
            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, pdng.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    color = Color.DarkGray,
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = 15.sp,
                )
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(parent.end, pdng.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = Color(40, 40, 40, 150),
                    fontFamily = FontFamily(Font(R.font.inter)), fontSize = 12.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(pdng.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }

    /** A boolean setting UI composable function. This either creates a CheckboxSetting or a ToggleSetting
     * based on the parameter [type] that is passed. If it's true, it is a checkbox setting, otherwise, a toggle button. */
    @Composable
    private fun BooleanSettingUI(modifier: Modifier = Modifier, type: Boolean) {
        val boolean = datastore().booleanFlow(key, defaultValue as Boolean).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    scope.launch {
                        datastore().writeBoolean(key, !boolean.value)
                    }
                }
        ) {

            val (ic, ttl, smry, utility, spacer) = createRefs()

            FancyIcon(icon = icon, size = icsize, modifier = Modifier.constrainAs(ic) {
                top.linkTo(parent.top, pdng.dp)
                start.linkTo(parent.start, pdng.dp)
            })

            val m = Modifier.constrainAs(utility) {
                top.linkTo(ttl.top)
                end.linkTo(parent.end, pdng.dp)
                bottom.linkTo(smry.bottom)
            }

            if (type) {
                Checkbox(
                    checked = boolean.value,
                    onCheckedChange = { b ->
                        scope.launch {
                            datastore().writeBoolean(key, b)
                        }
                    },
                    modifier = m
                )
            } else {
                Switch(
                    checked = boolean.value,
                    onCheckedChange = { b ->
                        scope.launch {
                            datastore().writeBoolean(key, b)
                        }
                    },
                    modifier = m
                )
            }

            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                    shadow = Shadow(offset = Offset(0f, 0f), blurRadius = 1f),
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (15.5).sp
                )
            )
            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    color = Color.DarkGray,
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = 15.sp,
                )
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = Color(40, 40, 40, 150),
                    fontFamily = FontFamily(Font(R.font.inter)), fontSize = 12.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(pdng.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }

    /** A multi-choice setting which shows a popup dialog when clicked.
     * @exception SettingCreationException When [entryKeys] and [entryValues] are not passed/specified. */
    @Composable
    private fun ListSettingUI(modifier: Modifier = Modifier) {
        val dialogOpen = remember { mutableStateOf(false) }
        val selectedItem = datastore().stringFlow(key, defaultValue as String).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()

        if (dialogOpen.value) {
            MultiChoiceDialog(
                items = entryKeys,
                title = title,
                onDismiss = { dialogOpen.value = false },
                selectedItem = entryValues.indexOf(selectedItem.value),
                onItemClick = { i ->
                    onItemChosen?.let { it(i, entryValues[i]) }
                    scope.launch {
                        datastore().writeString(key, entryValues[i])
                    }
                })
        }

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {
                    dialogOpen.value = true
                }
        ) {

            val (ic, ttl, smry, utility, spacer) = createRefs()

            FancyIcon(icon = icon, size = icsize, modifier = Modifier.constrainAs(ic) {
                top.linkTo(parent.top, pdng.dp)
                start.linkTo(parent.start, pdng.dp)
            })

            Icon(imageVector = Icons.Default.List, "", modifier = Modifier.constrainAs(utility) {
                top.linkTo(ttl.top)
                end.linkTo(parent.end, pdng.dp)
                bottom.linkTo(smry.bottom)
            })

            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                    shadow = Shadow(offset = Offset(0f, 0f), blurRadius = 1f),
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (15.5).sp
                )
            )
            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    color = Color.DarkGray,
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = 15.sp,
                )
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(utility.start, 6.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = Color(40, 40, 40, 150),
                    fontFamily = FontFamily(Font(R.font.inter)), fontSize = 12.sp,
                )
            )

            Spacer(modifier = Modifier
                .height(pdng.dp)
                .constrainAs(spacer) {
                    top.linkTo(smry.bottom)
                })
        }
    }

    /** A slider setting which has a draggable seek bar to choose an Integer value
     * @exception SettingCreationException */
    @Composable
    private fun SliderSettingUI(modifier: Modifier = Modifier) {
        val value = datastore().intFlow(key, defaultValue as Int).collectAsState(initial = defaultValue)
        val scope = rememberCoroutineScope()

        ConstraintLayout(
            modifier = modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = Paletting.SP_ORANGE)

                ) {

                }
        ) {

            val (ic, ttl, smry, slider, slidervalue, spacer) = createRefs()

            FancyIcon(icon = icon, size = icsize, modifier = Modifier.constrainAs(ic) {
                top.linkTo(parent.top, pdng.dp)
                start.linkTo(parent.start, pdng.dp)
            })

            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, pdng.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT),
                    shadow = Shadow(offset = Offset(0f, 0f), blurRadius = 1f),
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (15.5).sp
                )
            )

            Text(
                modifier = Modifier.constrainAs(ttl) {
                    top.linkTo(ic.top)
                    start.linkTo(ic.end, 6.dp)
                    end.linkTo(parent.end, pdng.dp)
                    width = Dimension.fillToConstraints
                },
                text = title,
                style = TextStyle(
                    color = Color.DarkGray,
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = 15.sp,
                )
            )

            Text(
                modifier = Modifier.constrainAs(smry) {
                    top.linkTo(ttl.bottom)
                    start.linkTo(ttl.start)
                    end.linkTo(parent.end, pdng.dp)
                    width = Dimension.fillToConstraints
                },
                text = summary,
                style = TextStyle(
                    color = Color(40, 40, 40, 150),
                    fontFamily = FontFamily(Font(R.font.inter)), fontSize = 12.sp,
                )
            )

            Slider(
                value = value.value.toFloat(),
                valueRange = (minValue.toFloat())..(maxValue.toFloat()),
                onValueChange = { f ->
                    scope.launch {
                        datastore().writeInt(key, f.toInt())
                    }
                }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .constrainAs(slider) {
                        top.linkTo(smry.bottom)
                        start.linkTo(parent.start, pdng.dp)
                        end.linkTo(slidervalue.start, pdng.dp)
                        width = Dimension.fillToConstraints
                    })

            Text(
                modifier = Modifier
                    .width(84.dp)
                    .constrainAs(slidervalue) {
                        top.linkTo(slider.top)
                        bottom.linkTo(slider.bottom)
                        end.linkTo(parent.end)
                    },
                text = (value.value).toString(),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily(Font(R.font.directive4bold)), fontSize = (13).sp
                )
            )

            Spacer(modifier = Modifier
                .height(pdng.dp)
                .constrainAs(spacer) {
                    top.linkTo(slider.bottom)
                })
        }
    }


    fun datastore(): DataStore<Preferences> {
        return DataStoreUtils.datastores[datastorekey]!!
    }
}