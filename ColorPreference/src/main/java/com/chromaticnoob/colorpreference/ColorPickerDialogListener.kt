package com.chromaticnoob.colorpreference

import androidx.annotation.ColorInt
import androidx.preference.PreferenceViewHolder

interface ColorPickerDialogListener {
    fun onAttached()

    fun onBindViewHolder(holder: PreferenceViewHolder?)

    fun onColorSelected(dialogId: Int, @ColorInt color: Int)

    fun onColorReset(dialogId: Int)

    fun onDialogDismissed(dialogId: Int)
}