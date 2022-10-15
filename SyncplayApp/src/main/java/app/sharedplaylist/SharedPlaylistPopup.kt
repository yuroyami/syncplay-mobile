package app.sharedplaylist

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import app.R
import app.controllers.activity.RoomActivity
import app.controllers.adapters.SharedPlaylistRecycAdapter
import app.databinding.FragmentSharedPlaylistBinding
import app.popups.SHPAddURLsPopup
import app.utils.UIUtils.bindTooltip

class SharedPlaylistPopup : Fragment(), SharedPlaylistCallback {

    private lateinit var binding: FragmentSharedPlaylistBinding

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = FragmentSharedPlaylistBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /** Binding callback to receive events */
        activity().bindSHPCallback(this)

        /** Reacting to button clicking for adding file(s) */
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

        /** Reacting to button clicking for adding URL(s) */
        binding.shPAddUrl.bindTooltip()
        binding.shPAddUrl.setOnClickListener {
            SHPAddURLsPopup(this).setBlurBackgroundEnable(true).showPopupWindow()
        }


        /** Reacting to button clicking for SHUFFLE */
        binding.shPShuffle.bindTooltip()
        binding.shPShuffle.setOnClickListener {

        }

        /** Reacting to OVERFLOW Button clicking */
        binding.shPOverflow.bindTooltip()
        binding.shPOverflow.setOnClickListener { v ->
            /** Instantiating the popup menu */
            val popup = PopupMenu(requireContext(), v)

            /** Making the group dividable and distinguishable */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                popup.menu.setGroupDividerEnabled(true)
            }

            val shuffle_All =
                popup.menu.add(0, 0, 0, getString(R.string.room_shared_playlist_button_shuffle))
            val shuffle_Rest = popup.menu.add(
                0,
                1,
                1,
                getString(R.string.room_shared_playlist_button_shuffle_rest)
            )

            val add_Files =
                popup.menu.add(1, 2, 2, getString(R.string.room_shared_playlist_button_add_file))
            val add_URLs =
                popup.menu.add(1, 3, 3, getString(R.string.room_shared_playlist_button_add_url))
            val add_Directory =
                popup.menu.add(1, 4, 4, getString(R.string.room_shared_playlist_button_add_folder))

            val playlistImport = popup.menu.add(
                2,
                5,
                5,
                getString(R.string.room_shared_playlist_button_playlist_import)
            )
            val playlistImportShf = popup.menu.add(
                2,
                6,
                6,
                getString(R.string.room_shared_playlist_button_playlist_import_n_shuffle)
            )
            val playlistExport = popup.menu.add(
                2,
                7,
                7,
                getString(R.string.room_shared_playlist_button_playlist_export)
            )

            val setMD = popup.menu.add(
                3,
                8,
                8,
                getString(R.string.room_shared_playlist_button_set_media_directories)
            )
            val setTD = popup.menu.add(
                3,
                9,
                9,
                getString(R.string.room_shared_playlist_button_set_trusted_domains)
            )

            popup.setOnMenuItemClickListener {
                when (it) {
                }
                return@setOnMenuItemClickListener true
            }
            popup.show()
        }
        /** Setting the adapter */
        binding.shPPlaylist.apply {
            adapter = SharedPlaylistRecycAdapter(activity(), activity().p.session.sharedPlaylist)
            (adapter as SharedPlaylistRecycAdapter).notifyDataSetChanged()
        }

    }

    override fun onUpdate() {
        activity().runOnUiThread {
            binding.shPPlaylist.adapter =
                SharedPlaylistRecycAdapter(activity(), activity().p.session.sharedPlaylist)
            (binding.shPPlaylist.adapter as SharedPlaylistRecycAdapter).notifyDataSetChanged()
        }
    }

    fun activity(): RoomActivity {
        return (requireActivity() as RoomActivity)
    }
}