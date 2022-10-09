package app.popups

import android.view.View
import android.widget.FrameLayout
import androidx.core.net.toUri
import app.R
import app.controllers.activity.RoomActivity
import app.controllers.activity.SoloActivity
import app.protocol.JsonSender
import app.utils.ExoPlayerUtils.injectVideo
import app.utils.RoomUtils.string
import app.utils.UIUtils.toasty
import app.wrappers.MediaFile
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import razerdp.basepopup.BasePopupWindow

class LoadURLPopup(val act1: RoomActivity?, val act2: SoloActivity?) : BasePopupWindow(act1 ?: act2) {
    init {
        setContentView(R.layout.popup_load_url)

        findViewById<MaterialButton>(R.id.url_confirm).setOnClickListener {
            it.visibility = View.GONE
            val url = findViewById<TextInputEditText>(R.id.url_edittext).text.toString().trim()
            if (url.isNotBlank()) {
                if (act1 == null) {
                    act2?.injectVideo(url)
                } else {
                    act1.injectVideo(url)
                    act1.p.file = MediaFile()
                    act1.p.file?.uri = url.toUri()
                    act1.p.file?.url = url
                    act1.p.file?.collectInfoURL()
                    act1.toasty(act1.string(R.string.room_selected_vid, "${act1.p.file?.fileName}"))
                    act1.p.sendPacket(JsonSender.sendFile(act1.p.file ?: return@setOnClickListener, act1))
                }
                dismiss()
            }
        }
        findViewById<FrameLayout>(R.id.loadurl_popup_dismisser).setOnClickListener {
            this.dismiss()
        }
    }
}