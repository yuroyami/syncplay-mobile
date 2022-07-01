package com.chromaticnoob.colorpreference;

import androidx.annotation.ColorInt;
import androidx.preference.PreferenceViewHolder;

/**
 * Callback used for getting the selected color from a color picker dialog.
 */
public interface ColorPickerDialogListener {

    void onAttached();

    void onBindViewHolder(PreferenceViewHolder holder);

    /**
     * Callback that is invoked when a color is selected from the color picker dialog.
     *
     * @param dialogId The dialog id used to create the dialog instance.
     * @param color    The selected color
     */
    void onColorSelected(int dialogId, @ColorInt int color);

    /**
     * Callback that is invoked when user clicks the reset button.
     *
     * @param dialogId The dialog id used to create the dialog instance.
     */
    void onColorReset(int dialogId);

    /**
     * Callback that is invoked when the color picker dialog was dismissed.
     *
     * @param dialogId The dialog id used to create the dialog instance.
     */
    void onDialogDismissed(int dialogId);
}
