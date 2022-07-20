package com.reddnek.syncplay.room

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.text.Html
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.*
import androidx.annotation.ColorInt
import androidx.annotation.UiThread
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.reddnek.syncplay.R
import com.reddnek.syncplay.room.RoomUtils.string
import com.reddnek.syncplayutils.SyncplayUtils
import com.reddnek.syncplayutils.utils.Message
import com.reddnek.syncplayutils.utils.User
import razerdp.basepopup.BasePopupWindow
import kotlin.math.roundToInt

/** Wrapping any UI-related functionality for RoomActivity here to reduce redundant code space **/

object UIUtils {

    /** This shows an OSD (On-screen Display) system/console information such as changing aspect ratio **/
    @JvmStatic
    @UiThread
    fun RoomActivity.displayInfo(msg: String) {
        roomBinding.syncplayInfoDelegate.clearAnimation()
        roomBinding.syncplayInfoDelegate.text = msg
        roomBinding.syncplayInfoDelegate.alpha = 1f
        roomBinding.syncplayInfoDelegate.animate()
            .alpha(0f)
            .setDuration(1000L)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    /** This broadcasts a message to show it in the message section **/
    @JvmStatic
    @UiThread
    fun RoomActivity.broadcastMessage(message: String, isChat: Boolean, chatter: String = "") {
        /** Messages are just a wrapper class for everything we need about a message
        So first, we initialize it, customize it, then add it to our long list of messages */
        val msg = Message()

        /** Check if it's a system or a user message **/
        if (isChat) msg.sender = chatter

        /** Check if the sender is also the main user, to determine colors **/
        //if (chatter.lowercase() == p.session.currentUsername.lowercase()) {
        msg.isMainUser = chatter == p.session.currentUsername

        /** Assigning the message content to the message **/
        msg.content = message /* Assigning message content to the variable inside our instance */

        /** Adding the message instance to our message sequence **/
        p.session.messageSequence.add(msg)

        /** Refresh views **/
        replenishMsgs(roomBinding.syncplayMESSAGERY)
    }

    /** Populates the Room Details section with users, their files, and their readiness */
    @JvmStatic
    fun RoomActivity.replenishUsers(linearLayout: LinearLayout) {
        runOnUiThread {
            if (roomBinding.syncplayOverviewcheckbox.isChecked) {
                linearLayout.removeAllViews()
                val userList = mutableListOf<User>().also {
                    it.addAll(p.session.userList)
                }

                //Creating line for room-name:
                val roomnameView = TextView(this)
                roomnameView.text =
                    string(R.string.room_details_current_room, p.session.currentRoom)
                roomnameView.isFocusable = false
                val linearlayout0 = LinearLayout(this)
                val linearlayoutParams0: LinearLayout.LayoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                linearlayout0.gravity = Gravity.END
                linearlayout0.orientation = LinearLayout.HORIZONTAL
                linearlayout0.addView(roomnameView)
                linearlayout0.isFocusable = false
                roomBinding.syncplayOverview.addView(linearlayout0, linearlayoutParams0)

                for (user in userList) {
                    //First line of user
                    val usernameView = TextView(this)
                    usernameView.text = user.name
                    if (user.name == p.session.currentUsername) {
                        usernameView.setTextColor(0xFFECBF39.toInt())
                        usernameView.setTypeface(usernameView.typeface, Typeface.BOLD)
                    }
                    val usernameReadiness: Boolean = user.readiness ?: false
                    val userIconette = ImageView(this)
                    userIconette.setImageResource(R.drawable.ic_user)
                    val userReadinessIcon = ImageView(this)
                    if (usernameReadiness) {
                        userReadinessIcon.setImageResource(R.drawable.ready_1)
                    } else {
                        userReadinessIcon.setImageResource(R.drawable.ready_0)
                    }

                    //Creating Linear Layout for 1st line
                    val linearlayout = LinearLayout(this)
                    val linearlayoutParams: LinearLayout.LayoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    linearlayout.gravity = Gravity.END
                    linearlayout.orientation = LinearLayout.HORIZONTAL
                    linearlayout.addView(usernameView)
                    linearlayout.addView(userIconette)
                    linearlayout.addView(userReadinessIcon)
                    linearlayout.isFocusable = false
                    usernameView.isFocusable = false
                    userIconette.isFocusable = false
                    userReadinessIcon.isFocusable = false
                    roomBinding.syncplayOverview.addView(linearlayout, linearlayoutParams)

                    //Second line (File name)
                    val isThereFile = user.file != null
                    val fileFirstLine =
                        if (isThereFile) user.file!!.fileName else getString(R.string.room_details_nofileplayed)

                    val lineArrower = ImageView(this)
                    lineArrower.setImageResource(R.drawable.ic_arrowleft)

                    val lineBlanker = ImageView(this)
                    lineBlanker.setImageResource(R.drawable.ic_blanker)

                    val lineFile = TextView(this)
                    lineFile.text = fileFirstLine
                    lineFile.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)

                    val linearlayout2 = LinearLayout(this)
                    val linearlayoutParams2: LinearLayout.LayoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    linearlayout2.gravity = Gravity.END
                    linearlayout2.orientation = LinearLayout.HORIZONTAL
                    linearlayout2.addView(lineFile)
                    linearlayout2.addView(lineArrower)
                    linearlayout2.addView(lineBlanker)
                    linearlayout2.isFocusable = false
                    lineFile.isFocusable = false
                    lineArrower.isFocusable = false
                    lineBlanker.isFocusable = false
                    roomBinding.syncplayOverview.addView(linearlayout2, linearlayoutParams2)

                    //Third Line (file info)
                    if (isThereFile) {
                        val fileSize = user.file?.fileSize?.toDoubleOrNull()?.div(1000000.0)
                        val fileInfoLine = string(
                            R.string.room_details_file_properties,
                            SyncplayUtils.timeStamper(user.file?.fileDuration?.roundToInt()!!),
                            fileSize?.toString() ?: "???"
                        )
                        val lineFileInfo = TextView(this)
                        lineFileInfo.text = fileInfoLine
                        lineFileInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)

                        val linearlayout3 = LinearLayout(this)
                        val linearlayoutParams3: LinearLayout.LayoutParams =
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        linearlayout3.gravity = Gravity.END
                        linearlayout3.orientation = LinearLayout.HORIZONTAL
                        linearlayout3.addView(lineFileInfo)
                        linearlayout3.isFocusable = false
                        lineFileInfo.isFocusable = false
                        for (f in (0 until 2)) {
                            val lineBlanker3 = ImageView(this)
                            lineBlanker3.setImageResource(R.drawable.ic_blanker)
                            linearlayout3.addView(lineBlanker3)
                            lineBlanker3.isFocusable = false
                        }
                        roomBinding.syncplayOverview.addView(linearlayout3, linearlayoutParams3)
                    }
                }
            }
        }
    }

    /** Populates the message section with the last available messages **/
    /** FIXME: inflate a ready-layout from layout resources rather than creating it programmatically **/
    @JvmStatic
    fun RoomActivity.replenishMsgs(rltvLayout: RelativeLayout) {
        runOnUiThread {
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
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    txtview.text =
                        Html.fromHtml(message.factorize(isTimestampEnabled, this@replenishMsgs))
                } else {
                    txtview.text = Html.fromHtml(
                        message.factorize(isTimestampEnabled, this@replenishMsgs),
                        Html.FROM_HTML_MODE_LEGACY
                    )
                }
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
                roomBinding.syncplayMESSAGERY.also {
                    it.clearAnimation()
                    it.visibility = View.VISIBLE
                    it.alpha = 1f
                }
                if (!roomBinding.vidplayer.isControllerVisible) {
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
    @JvmStatic
    @UiThread
    fun RoomActivity.hideKb() {
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        roomBinding.syncplayINPUTBox.clearFocus()
    }

    /** Applies UI settings that are being changed through the in-room settings dialog **/
    @JvmStatic
    @UiThread
    fun RoomActivity.applyUISettings() {
        /* For settings: Timestamp,Message Count,Message Font Size */
        replenishMsgs(roomBinding.syncplayMESSAGERY)

        /* Holding a reference to SharedPreferences to use it later */
        val sp = PreferenceManager.getDefaultSharedPreferences(this)

        /* Applying "overview_alpha" setting */
        val alpha1 = sp.getInt("overview_alpha", 40) //between 0-255
        @ColorInt val alphaColor1 = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha1)
        roomBinding.syncplayOverviewCard.setCardBackgroundColor(alphaColor1)

        /* Applying MESSAGERY Alpha **/
        val alpha2 = sp.getInt("messagery_alpha", 40) //between 0-255
        @ColorInt val alphaColor2 = ColorUtils.setAlphaComponent(Color.DKGRAY, alpha2)
        roomBinding.syncplayMESSAGERYOpacitydelegate.setCardBackgroundColor(alphaColor2)

        /* Applying Subtitle Size setting */
        ccsize = sp.getInt("subtitle_size", 18).toFloat()
        roomBinding.vidplayer.subtitleView?.setFixedTextSize(COMPLEX_UNIT_SP, ccsize)
    }

    /** Convenience method to save code space */
    @JvmStatic
    fun Activity.toasty(string: String) {
        runOnUiThread {
            Toast.makeText(this, string, Toast.LENGTH_LONG).show()
        }
    }

    /** Used to save code space to show a popup with a few settings */
    @JvmStatic
    fun RoomActivity.showPopup(popup: BasePopupWindow, animate: Boolean) {
        runOnUiThread {
            popup
                .setBlurBackgroundEnable(true)
                .setOutSideTouchable(true)
                .setOutSideDismiss(true)
            if (!animate) {
                popup.showAnimation = null
            }
            popup.showPopupWindow()
        }
    }


    /** Shows a tooltip above every button to define its functionality after a long-press
     * This should not be called on Android L & M as it would freeze the device if done twice
     */
    @JvmStatic
    fun ImageButton.attachTooltip(string: String) {
        if (VERSION.SDK_INT > VERSION_CODES.M) {
            this.isLongClickable = true
            TooltipCompat.setTooltipText(this, string)
        }
    }

}