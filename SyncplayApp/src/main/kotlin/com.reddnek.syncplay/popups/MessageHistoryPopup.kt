package com.reddnek.syncplay.popups

import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.reddnek.syncplay.R
import com.reddnek.syncplay.controllers.activity.RoomActivity
import com.reddnek.syncplay.controllers.adapters.HistoryRecycAdapter
import razerdp.basepopup.BasePopupWindow

class MessageHistoryPopup(val activity: RoomActivity) : BasePopupWindow(activity) {
    init {
        setContentView(R.layout.popup_messages)

        val dismisser = findViewById<FrameLayout>(R.id.messages_popup_dismisser)
        dismisser.setOnClickListener {
            this.dismiss()
        }
    }

    override fun onShowing() {
        super.onShowing()

        findViewById<RecyclerView>(R.id.syncplay_MESSAGEHISTORY_recyc).adapter =
            HistoryRecycAdapter(activity.p.session.messageSequence, activity)

    }
}