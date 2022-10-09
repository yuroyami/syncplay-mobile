package app.sharedplaylist

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import app.R
import app.controllers.adapters.DirectoriesAdapter
import app.databinding.ActivityDirectoriesBinding
import app.utils.MiscUtils
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class DirectoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDirectoriesBinding

    private val gson: Gson = GsonBuilder().create()

    companion object {
        const val prefKey = "SHARED_PLAYLIST_MEDIA_DIRECTORIES"

        @JvmStatic
        fun getFolderList(gson: Gson, prefKey: String, context: Context): MutableList<String> {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)

            /** Since SharedPreferences do not support saving a coherent ordered list, we use JSON */
            val folderJson = sp.getString(prefKey, "[]")
            return gson.fromJson<List<String>>(folderJson, List::class.java).toMutableList()
        }

        @JvmStatic
        fun writeFolderList(list: List<String>, gson: Gson, prefKey: String, context: Context) {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val newList = gson.toJson(list)
            sp.edit().putString(prefKey, newList).apply()
        }
    }

    private val directoryPickResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result?.resultCode == Activity.RESULT_OK) {
                val folderUri = result.data?.data

                /** These tow lines are super-important, especially on SAF (Storage Access Frameork)
                 * Why ? Because Uris have such a short lifespan that you can't use them even seconds
                 * after having been offered the Uris, and using it is like using a piece of random
                 * string. Therefore, you need to use the takePersistableUriPermission in order
                 * to obtain permanent access to Uri across all activities.
                 */
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(
                    folderUri!!,
                    takeFlags
                )

                val folders = getFolderList(gson, prefKey, this)
                folders.add(folderUri.toString())
                writeFolderList(folders, gson, prefKey, this)

                refreshList()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDirectoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* Immersive Mode */
        MiscUtils.hideSystemUI(this, false)

        /** Opening the file picker activity, but with a folder pick intent */
        binding.addElement.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_OPEN_DOCUMENT_TREE
            intent.flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            directoryPickResult.launch(intent)
        }

        /** Managing the GridView layout initialization */
        refreshList()

        /** Managing item clicking in GridView */
        binding.folders.setOnItemClickListener { _, view, i, _ ->
            val itemPopup = PopupMenu(this, view)

            val fullPath =
                itemPopup.menu.add(0, 0, 0, getString(R.string.media_directories_show_full_path))
            fullPath.icon = AppCompatResources.getDrawable(this, R.drawable.ic_path)

            val delete =
                itemPopup.menu.add(0, 1, 0, this.getString(R.string.media_directories_delete))
            delete.icon = AppCompatResources.getDrawable(this, R.drawable.ic_delete)

            itemPopup.setOnMenuItemClickListener {
                val uris = (binding.folders.adapter as DirectoriesAdapter).uris

                when (it) {
                    fullPath -> {
                        Snackbar.make(
                            this,
                            binding.root,
                            uris[i].toUri().path?.replace("/tree/primary:", "Storage//").toString(),
                            -1
                        ).show()
                    }
                    delete -> {
                        var itemToRemove = ""
                        for (uri in uris) {
                            val shortUri = uri.toUri().lastPathSegment?.substringAfterLast("/")
                                ?.substringAfter("primary:")
                            if (shortUri == (view.findViewById<TextView>(R.id.folder_path).text)) {
                                itemToRemove = uri
                                break
                            }
                        }
                        uris.remove(itemToRemove)
                        writeFolderList(uris, gson, prefKey, this)
                        refreshList()
                    }
                }
                return@setOnMenuItemClickListener true
            }
            itemPopup.show()
        }
        /** Save Button basically finishes the activity */
        binding.directoriesSave.setOnClickListener { finish() }

        /** Clear All replaces the set of uris in our SharedPreferences with an empty set */
        binding.clearAll.setOnClickListener {
            val clearDialog = AlertDialog.Builder(this)
            val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        dialog.dismiss()
                        PreferenceManager
                            .getDefaultSharedPreferences(this)
                            .edit()
                            .putString(prefKey, "[]")
                            .apply()
                        refreshList()
                    }
                    DialogInterface.BUTTON_NEGATIVE -> {
                        dialog.dismiss()
                    }
                }
            }

            clearDialog.setMessage(getString(R.string.media_directories_clear_all_confirm))
                .setPositiveButton(getString(R.string.yes), dialogClickListener)
                .setNegativeButton(getString(R.string.no), dialogClickListener)
                .show()
        }
    }

    private fun refreshList() {
        /* Adapters giving the position of some item can be really buggy -
            According to my experience, it's much better and bug-free to reassign a whole new adapter
         */
        binding.folders.adapter = DirectoriesAdapter(
            this, getFolderList(
                gson,
                prefKey, this
            )
        )
    }
}