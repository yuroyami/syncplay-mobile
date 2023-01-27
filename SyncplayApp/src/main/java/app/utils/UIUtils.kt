package app.utils

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import app.ui.activities.RoomActivity
import app.ui.activities.WatchActivity
import app.wrappers.Constants
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

    /** Populates the message section with the last available messages **/
    /** FIXME: inflate a ready-layout from layout resources rather than creating it programmatically **/
    fun RoomActivity.replenishMsgs(rltvLayout: RelativeLayout) {
        lifecycleScope.launch(Dispatchers.Main) {
            rltvLayout.removeAllViews() /* First, we clean out the current messages */
            val isTimestampEnabled = PreferenceManager
                .getDefaultSharedPreferences(this@replenishMsgs)
                .getBoolean("ui_timestamp", true)
            val maxMsgsCount = PreferenceManager
                .getDefaultSharedPreferences(this@replenishMsgs)
                .getInt("msg_count", 12) /* We obtain max count, determined by user */

            val msgs = p.session.messageSequence.takeLast(maxMsgsCount)

            for (message in msgs) {
                val msgPosition: Int = msgs.indexOf(message)

                val txtview = TextView(this@replenishMsgs)
                txtview.text = Html.fromHtml(
                    message.factorize(includeTimestamp = isTimestampEnabled, context = this@replenishMsgs).toString(),
                    Html.FROM_HTML_MODE_LEGACY
                )
                txtview.textSize = PreferenceManager.getDefaultSharedPreferences(this@replenishMsgs)
                    .getInt("msg_size", 12).toFloat()

                val rltvParams: RelativeLayout.LayoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                rltvParams.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
                rltvParams.marginStart = 4
                txtview.id = View.generateViewId()

                val msgFadeTimeout =
                    PreferenceManager.getDefaultSharedPreferences(this@replenishMsgs)
                        .getInt("message_persistance", 3).toLong() * 1000
                if (message != msgs[0]) {
                    rltvParams.addRule(
                        RelativeLayout.BELOW,
                        rltvLayout.getChildAt(msgPosition - 1).id
                    )
                }
                rltvLayout.addView(txtview, msgPosition, rltvParams)

                //Animate
                //binding.syncplayMESSAGERY.also {
                //it.clearAnimation()
                //it.visibility = View.VISIBLE
                //it.alpha = 1f
                //}
                if (true /* !binding.vidplayer.isControllerVisible */) {
                    if (message == msgs.last()) {
                        txtview.clearAnimation()
                        txtview.alpha = 1f
                        txtview.animate()
                            .alpha(0f)
                            .setDuration(msgFadeTimeout)
                            .setInterpolator(AccelerateInterpolator())
                            .start()
                    } else {
                        txtview.clearAnimation()
                        txtview.alpha = 0f
                    }
                }
            }
        }
    }

    /** Hides the keyboard and loses message typing focus **/
    fun WatchActivity.hideKb() {
        lifecycleScope.launch(Dispatchers.Main) {
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
    }

    /** Applies UI settings that are being changed through the in-room settings dialog **/
    fun RoomActivity.applyUISettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            /* For settings: Timestamp,Message Count,Message Font Size */
            //replenishMsgs(binding.syncplayMESSAGERY)

            /* Holding a reference to SharedPreferences to use it later */
            val sp = PreferenceManager.getDefaultSharedPreferences(this@applyUISettings)

            /* Applying "overview_alpha" setting */
            val alpha1 = sp.getInt("overview_alpha", 40) //between 0-255
            @ColorInt val alphaColor1 = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha1)
            //binding.syncplayOverviewCard.setCardBackgroundColor(alphaColor1)

            /* Applying MESSAGERY Alpha **/
            val alpha2 = sp.getInt("messagery_alpha", 40) //between 0-255
            @ColorInt val alphaColor2 = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha2)
            //binding.syncplayMESSAGERYOpacitydelegate.setCardBackgroundColor(alphaColor2)

            /* Applying Subtitle Size setting */
            this@applyUISettings.ccsize = sp.getInt("subtitle_size", 18).toFloat()
            //binding.vidplayer.subtitleView?.setFixedTextSize(COMPLEX_UNIT_SP, ccsize)
        }
    }

    /** Convenience method to save code space */
    fun Context.toasty(string: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this@toasty, string, Toast.LENGTH_SHORT).show()
        }
    }

    /** Inserts a popup into the pseudo popup container (Which is not really a popup but a fragment
     * @param int The popup index to show (1 = In-room Settings | 2 = Shared Playlist) */
    fun RoomActivity.insertPopup(int: Constants.POPUP) {
        when (int) {
            Constants.POPUP.POPUP_INROOM_SETTINGS -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.pseudo_popup_container, RoomSettingsHosterFragment())
//                    .commitNowAllowingStateLoss()

            }

            Constants.POPUP.POPUP_SHARED_PLAYLIST -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.pseudo_popup_container, SHPFragment())
//                    .commitNowAllowingStateLoss()

            }

            Constants.POPUP.POPUP_MESSAGE_HISTORY -> {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.pseudo_popup_container, MessageHistoryPopup())
//                    .commitNowAllowingStateLoss()

            }
        }
        activePseudoPopup = int
        //binding.pseudoPopupParent.visibility = View.VISIBLE
    }


    /** Shows a tooltip above every button to define its functionality after a long-press
     * This should not be called on Android L & M as it would freeze the device if done twice
     */
    fun View.attachTooltip(string: String) {
        this.isLongClickable = true
        TooltipCompat.setTooltipText(this, string)
    }

    fun View.bindTooltip() {
        this.isLongClickable = true
        val tooltip = this.contentDescription
        TooltipCompat.setTooltipText(this, tooltip)
    }

}