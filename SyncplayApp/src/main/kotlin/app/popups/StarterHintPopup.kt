package app.popups

import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import app.R
import com.google.android.material.button.MaterialButton
import razerdp.basepopup.BasePopupWindow

class StarterHintPopup(val activity: AppCompatActivity) : BasePopupWindow(activity) {
    init {
        setContentView(R.layout.popup_starter_hint)
        findViewById<MaterialButton>(R.id.dont_show_again).setOnClickListener {
            PreferenceManager.getDefaultSharedPreferences(activity)
                .edit().putBoolean("dont_show_starter_hint", true).apply()
            this.dismiss()
        }

        findViewById<FrameLayout>(R.id.starter_hint_dismisser).setOnClickListener {
            this.dismiss()
        }
    }
}