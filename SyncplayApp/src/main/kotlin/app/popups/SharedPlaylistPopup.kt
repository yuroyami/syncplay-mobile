package app.popups

import android.content.Intent
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.RecyclerView
import app.R
import app.controllers.activity.RoomActivity
import app.controllers.adapters.SharedPlaylistRecycAdapter
import com.google.android.material.button.MaterialButton
import razerdp.basepopup.BasePopupWindow

class SharedPlaylistPopup(val activity: RoomActivity) : BasePopupWindow(activity) {
    init {
        val v = setContentView(R.layout.popup_shared_playlist)

        /* Dismissal */
        val dismisser = this.findViewById<FrameLayout>(R.id.shP_dismisser)
        dismisser.setOnClickListener {
            this.dismiss()
        }

        /* Reacting to button clicking for adding a file and a folder */
        this.findViewById<AppCompatImageButton>(R.id.shP_add_file).setOnClickListener {
            val intent = Intent()
            intent.type = "*/*"
            intent.action = Intent.ACTION_OPEN_DOCUMENT
            activity.sharedFileResult.launch(intent)
        }
        this.findViewById<AppCompatImageButton>(R.id.shP_add_directory).setOnClickListener {
            val intent = Intent().apply { action = Intent.ACTION_OPEN_DOCUMENT_TREE }
            activity.sharedFolderResult.launch(intent)
        }

        this.findViewById<RecyclerView>(R.id.shP_playlist).apply {
            adapter = SharedPlaylistRecycAdapter(activity, activity.p.session.sharedPlaylist)
            (adapter as SharedPlaylistRecycAdapter).notifyDataSetChanged()
        }

        this.findViewById<MaterialButton>(R.id.shP_save).setOnClickListener {
            dismiss()
        }
    }

    override fun update() {
        activity.runOnUiThread {
            this.findViewById<RecyclerView>(R.id.shP_playlist)?.apply {
                adapter =
                    SharedPlaylistRecycAdapter(activity, activity.p.session.sharedPlaylist)
                (adapter as SharedPlaylistRecycAdapter).notifyDataSetChanged()
            }
            super.update()
        }
    }
}