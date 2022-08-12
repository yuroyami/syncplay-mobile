package com.reddnek.syncplay.popups

import android.os.Build
import android.text.Html
import android.util.Log
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.reddnek.syncplay.R
import com.reddnek.syncplay.room.RoomActivity
import razerdp.basepopup.BasePopupWindow

class MessageHistoryPopup(val activity: RoomActivity) : BasePopupWindow(activity) {
    init {
        setContentView(R.layout.popup_messages)

        /* Getting parent and clearing it */
        val parent = findViewById<LinearLayout>(R.id.syncplay_MESSAGEHISTORY)
        parent.removeAllViews()

        val msgs = activity.p.session.messageSequence
        for (message in msgs) {
            /* Creating 1 TextView for each message */
            val txtview = AppCompatTextView(activity)
            txtview.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                Html.fromHtml(
                    message.factorize(true, activity),
                    Html.FROM_HTML_MODE_LEGACY
                ) else Html.fromHtml(message.factorize(true, activity))
            txtview.textSize = 9F

            parent.addView(txtview)
            Log.e("Added view", "Childcount: ${msgs.size}, View index: ${msgs.indexOf(message)}")
        }

        val dismisser = findViewById<FrameLayout>(R.id.messages_popup_dismisser)
        dismisser.setOnClickListener {
            this.dismiss()
        }
    }
}