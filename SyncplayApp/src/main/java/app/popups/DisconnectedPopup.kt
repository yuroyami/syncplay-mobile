package app.popups

import android.widget.FrameLayout
import app.R
import app.ui.activities.RoomActivity
import razerdp.basepopup.BasePopupWindow

class DisconnectedPopup(val activity: RoomActivity) : BasePopupWindow(activity) {
    init {
        setContentView(R.layout.popup_disconnected)
        val dismisser = findViewById<FrameLayout>(R.id.disconnected_popup_dismisser)
        dismisser.setOnClickListener {
            this.dismiss()
        }
    }
}