package app.popups

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.controllers.activity.RoomActivity
import app.controllers.adapters.SharedPlaylistRecycAdapter
import app.databinding.PopupSharedPlaylistBinding
import app.utils.UIUtils.bindTooltip

class SharedPlaylistPopup : Fragment() {

    private lateinit var binding: PopupSharedPlaylistBinding

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = PopupSharedPlaylistBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /** Reacting to button clicking for adding a file(s) */
        binding.shPAddFile.bindTooltip()
        binding.shPAddFile.setOnClickListener {
            val intent = Intent()
            intent.type = "*/*"
            intent.action = Intent.ACTION_OPEN_DOCUMENT
            activity().sharedFileResult.launch(intent)
        }

        /** Reacting to button clicking for adding a directory */
        binding.shPAddDirectory.bindTooltip()
        binding.shPAddDirectory.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
            activity().sharedFolderResult.launch(intent)
        }

        /** Setting the adapter */
        binding.shPPlaylist.apply {
            adapter = SharedPlaylistRecycAdapter(activity(), activity().p.session.sharedPlaylist)
            (adapter as SharedPlaylistRecycAdapter).notifyDataSetChanged()
        }

    }

    fun update() {
        activity().runOnUiThread {
            binding.shPPlaylist.adapter = SharedPlaylistRecycAdapter(activity(), activity().p.session.sharedPlaylist)
            (binding.shPPlaylist.adapter as SharedPlaylistRecycAdapter).notifyDataSetChanged()
        }
    }

    fun activity(): RoomActivity {
        return (requireActivity() as RoomActivity)
    }
}