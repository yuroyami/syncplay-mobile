package com.chromaticnoob.colorpreference

import android.content.Context
import android.util.TypedValue

internal object DrawingUtils {
    @JvmStatic
    fun dpToPx(c: Context, dipValue: Float): Int {
        val metrics = c.resources.displayMetrics
        val `val` = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
        val res = (`val` + 0.5).toInt()
        return if (res == 0 && `val` > 0) 1 else res
    }
}