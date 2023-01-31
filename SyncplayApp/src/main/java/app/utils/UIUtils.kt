package app.utils

import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.lifecycle.lifecycleScope
import app.activities.RoomActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Wrapping any UI-related functionality for RoomActivity here to reduce redundant code space **/

object UIUtils {

    /** This shows an OSD (On-screen Display) system/console information such as changing aspect ratio **/
    fun RoomActivity.displayInfo(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
//            binding.syncplayInfoDelegate.clearAnimation()
//            binding.syncplayInfoDelegate.text = msg
//            binding.syncplayInfoDelegate.alpha = 1f
//            binding.syncplayInfoDelegate.animate()
//                .alpha(0f)
//                .setDuration(1000L)
//                .setInterpolator(AccelerateInterpolator())
//                .start()
        }
    }

    /** Shows a tooltip above every button to define its functionality after a long-press
     * This should not be called on Android L & M as it would freeze the device if done twice
     */
    fun View.attachTooltip(string: String) {
        this.isLongClickable = true
        TooltipCompat.setTooltipText(this, string)
    }

    fun View.bindTooltip() {
        attachTooltip(this.contentDescription.toString())
    }

}