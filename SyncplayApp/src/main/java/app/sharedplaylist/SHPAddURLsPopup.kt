package app.sharedplaylist

import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import app.R
import app.sharedplaylist.SHPUtils.addURLs
import app.utils.UIUtils.toasty
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import razerdp.basepopup.BasePopupWindow

class SHPAddURLsPopup(val fragment: SHPFragment) : BasePopupWindow(fragment) {
    init {
        setContentView(R.layout.popup_shp_add_urls)

        val edittext = findViewById<TextInputEditText>(R.id.urls_edittext)

        findViewById<MaterialButton>(R.id.urls_confirm).setOnClickListener {
            it.visibility = View.GONE
            val url = edittext.text.toString().trim()
            if (url.isNotBlank()) {
                fragment.activity().addURLs(url.split("\n"))
                dismiss()
            }
        }
        findViewById<ImageButton>(R.id.urls_paste).setOnClickListener {
            edittext.setText((context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).text.toString())
            context.toasty("Pasted clipboard content")
        }

        findViewById<FrameLayout>(R.id.addurls_popup_dismisser).setOnClickListener {
            this.dismiss()
        }
    }
}