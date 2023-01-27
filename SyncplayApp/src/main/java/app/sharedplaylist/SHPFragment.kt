package app.sharedplaylist

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import app.R
import app.databinding.FragmentSharedPlaylistBinding
import app.ui.activities.RoomActivity
import app.utils.MiscUtils.getFileName
import app.utils.SharedPlaylistUtils.loadSHP
import app.utils.UIUtils.bindTooltip
import app.utils.UIUtils.toasty

class SHPFragment : Fragment(), SHPCallback {

    private lateinit var binding: FragmentSharedPlaylistBinding

    /** Set to true when the [SharedPlaylistUtils.loadSHP] method should know that it should shuffle the txt file */
    var shuffle = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = FragmentSharedPlaylistBinding.inflate(i, c, false)
        return binding.root
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /** Binding callback to receive events */
        activity().bindSHPCallback(this)

        /** Setting the adapter */
        //binding.shPPlaylist.adapter = SharedPlaylistRecycAdapter(activity(), activity().p.session.sharedPlaylist)

        /** Reacting to button clicking for adding file(s) */
        binding.shPAddFile.bindTooltip()
        binding.shPAddFile.setOnClickListener {
            actionAddFiles()
        }

        /** Reacting to button clicking for adding a directory */
        binding.shPAddDirectory.bindTooltip()
        binding.shPAddDirectory.setOnClickListener {
            actionAddFolder()
        }

        /** Reacting to button clicking for adding URL(s) */
        binding.shPAddUrl.bindTooltip()
        binding.shPAddUrl.setOnClickListener {
            actionAddURLs()
        }

        /** Reacting to button clicking for SHUFFLE */
        binding.shPShuffle.bindTooltip()
        binding.shPShuffle.setOnClickListener {
            actionShuffleAll()
        }

        /** Reacting to OVERFLOW Button clicking */
        binding.shPOverflow.bindTooltip()
        binding.shPOverflow.setOnClickListener { v ->
            /** Instantiating the popup menu */
            val popup = PopupMenu(ContextThemeWrapper(requireContext(), R.style.MenuStyle), v)
            (popup.menu as MenuBuilder).setOptionalIconsVisible(true)
            /** Making icons visible */

            /** Making the group dividable and distinguishable */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                popup.menu.setGroupDividerEnabled(true)
            }


            /** Creating popup menu items under their respective groups */
            val shuffleRest = popup.menu.add(0, 0, 0, getString(R.string.room_shared_playlist_button_shuffle_rest))
            val shuffleAll = popup.menu.add(0, 1, 1, getString(R.string.room_shared_playlist_button_shuffle))

            val addFiles = popup.menu.add(1, 2, 2, getString(R.string.room_shared_playlist_button_add_file))
            val addURLs = popup.menu.add(1, 3, 3, getString(R.string.room_shared_playlist_button_add_url))
            val addDirectory = popup.menu.add(1, 4, 4, getString(R.string.room_shared_playlist_button_add_folder))

            val playlistImport = popup.menu.add(2, 5, 5, getString(R.string.room_shared_playlist_button_playlist_import))
            val playlistImportShf = popup.menu.add(2, 6, 6, getString(R.string.room_shared_playlist_button_playlist_import_n_shuffle))
            val playlistExport = popup.menu.add(2, 7, 7, getString(R.string.room_shared_playlist_button_playlist_export))

            val setMD = popup.menu.add(3, 8, 8, getString(R.string.room_shared_playlist_button_set_media_directories))
            //val setTD = popup.menu.add(3, 9, 9, getString(R.string.room_shared_playlist_button_set_trusted_domains))

            //val undo = popup.menu.add(4, 10, 10, getString(R.string.room_shared_playlist_button_undo))
            val clearList = popup.menu.add(4, 11, 11, "Clear the playlist")

            /** Assigning some icons to the menu items */
            shuffleAll.setIcon(R.drawable.ic_shuffle)

            shuffleRest.setIcon(R.drawable.ic_shuffle)

            addFiles.setIcon(R.drawable.ic_shared_playlist_add)
            addURLs.setIcon(R.drawable.ic_url)
            addDirectory.setIcon(R.drawable.ic_add_folder)

            playlistImport.setIcon(R.drawable.file_import)
            playlistImportShf.setIcon(R.drawable.file_import_shf)
            playlistExport.setIcon(R.drawable.save)

            setMD.setIcon(R.drawable.ic_folder)
            //setTD.setIcon(R.drawable.ic_domain)
            //undo.setIcon(R.drawable.ic_undo)
            clearList.setIcon(R.drawable.ic_clear_all)

            popup.setOnMenuItemClickListener {
                when (it) {
                    shuffleRest -> actionShuffleRest()
                    shuffleAll -> actionShuffleAll()
                    addFiles -> actionAddFiles()
                    addURLs -> actionAddURLs()
                    addDirectory -> actionAddFolder()
                    playlistImport -> actionPlaylistImport(false)
                    playlistImportShf -> actionPlaylistImport(true)
                    playlistExport -> actionPlaylistExport()
                    setMD -> actionSetMD()
                    //setTD -> actionsetTD()
                    //undo -> actionUndo()
                    clearList -> activity() //.clearPlaylist()
                }
                return@setOnMenuItemClickListener true
            }
            popup.show()
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    /** Our data set is light-weight, this will cause no issues */
    override fun onUpdate() {
        requireActivity().runOnUiThread {
            binding.shPPlaylist.adapter?.notifyDataSetChanged()
        }
    }

    fun activity(): RoomActivity {
        return (requireActivity() as RoomActivity)
    }

    /****** The following methods carry out userbased operations such as shuffling, adding, removing, etc **/
    /** Adding one or multiple files to the playlist */
    fun actionAddFiles() {
        val intent = Intent()
        intent.type = "*/*"
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        activity().sharedFileResult.launch(intent)
    }

    /** Adding one or multiple URLs to the playlist (this opens a popup) */
    fun actionAddURLs() {
        //SHPAddURLsPopup(this).setBlurBackgroundEnable(true).showPopupWindow()
    }

    /** Follows the action of clicking add folder */
    fun actionAddFolder() {
        val intent = Intent()
        intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
        activity().sharedFolderResult.launch(intent)
    }

    /** This will shuffle the entire playlist */
    fun actionShuffleAll() {
        activity() //.shuffle(false)
    }

    /** This will shuffle the remaining items of the playlist */
    fun actionShuffleRest() {
        activity()//.shuffle(true)
    }

    /** This will import a playlist from a txt or m3u8 file, with shuffling as a boolean option */
    fun actionPlaylistImport(shuffle: Boolean) {
        val intent = Intent()
        intent.type = "text/plain"
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        this.shuffle = shuffle
        loadResult.launch(intent)
    }

    /** This will export the current playlist to the storage, doesn't need full storage access */
    fun actionPlaylistExport() {
        /** Checking whether the shared playlist is empty, if so, no need to proceed */
        if (activity().p.session.sharedPlaylist.isEmpty()) {
            requireContext().toasty("Shared Playlist is empty. Nothing to save.")
            return
        }

        /** Launching intent to select destination folder */
        val intent = Intent()
        intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
        saveResult.launch(intent)
    }

    /** Executes the action of modifying media directories */
    fun actionSetMD() {
        startActivity(Intent(requireContext(), DirectoriesActivity::class.java))
    }

    /** Opens a little popup to edit trusted domains */
    fun actionsetTD() {

    }

    /** This will undo the last change that happened to the playlist, and will restore the previous state */
    fun actionUndo() {

    }

    val saveResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                activity().contentResolver.takePersistableUriPermission(uri, takeFlags)
                activity()//.saveSHP(uri)
            }
        }

    val loadResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val path = result.data?.data!!
                val filename = activity().getFileName(path).toString()
                val extension = filename.substring(filename.length - 4)
                if (extension == ".txt") {
                    activity()//.loadSHP(path, shuffle)
                    shuffle = false
                } else {
                    requireActivity().toasty("Error: Not a valid plain text file (.txt)")
                }
            }
        }
}