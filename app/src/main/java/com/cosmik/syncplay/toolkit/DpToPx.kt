package com.cosmik.syncplay.toolkit

import android.content.Context
import android.util.DisplayMetrics

class DpToPx(val dp: Float, val contexT: Context?) {

    fun convertUnit(): Float {
        val resources = contexT?.resources
        val metrics = resources?.displayMetrics
        return dp * (metrics?.densityDpi?.toFloat()?.div(DisplayMetrics.DENSITY_DEFAULT)!!)
    }
}