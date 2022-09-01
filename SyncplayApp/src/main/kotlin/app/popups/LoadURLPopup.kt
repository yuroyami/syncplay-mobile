package app.popups

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import app.R
import app.controllers.activity.RoomActivity
import app.utils.ExoPlayerUtils.injectVideo
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import razerdp.basepopup.BasePopupWindow

class LoadURLPopup(val activity: AppCompatActivity) : BasePopupWindow(activity) {
    init {
        setContentView(R.layout.popup_load_url)

        findViewById<MaterialButton>(R.id.url_confirm).setOnClickListener {
            it.visibility = View.GONE
            val url = findViewById<TextInputEditText>(R.id.url_edittext).text.toString().trim()
            if (url.isNotBlank()) {
                (activity as RoomActivity).injectVideo(url)
                dismiss()
            }
        }
        findViewById<FrameLayout>(R.id.loadurl_popup_dismisser).setOnClickListener {
            this.dismiss()
        }


    }
}