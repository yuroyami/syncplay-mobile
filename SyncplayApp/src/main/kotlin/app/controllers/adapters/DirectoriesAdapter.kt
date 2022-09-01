package app.controllers.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import app.R
import app.controllers.activity.DirectoriesActivity
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class DirectoriesAdapter(private val activity: DirectoriesActivity, var uris: MutableList<String>) :
    BaseAdapter() {

    val gson: Gson = GsonBuilder().create()

    /* Total number of folders contained within the adapter   */
    override fun getCount(): Int {
        return uris.size
    }

    override fun getItem(position: Int): Any? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View?, parent: ViewGroup?): View {
        lateinit var item: ConstraintLayout;
        if (view == null) {
            item = activity.layoutInflater.inflate(
                R.layout.activity_directories_item,
                null
            ) as ConstraintLayout
            var text = uris[position].toUri().lastPathSegment?.substringAfterLast("/")
            if (text?.contains("primary:") == true) text = text.substringAfter("primary:")
            item.findViewById<TextView>(R.id.folder_path).text = text
            notifyDataSetChanged()
        } else {
            item = view as ConstraintLayout
        }

        return item
    }

    override fun notifyDataSetChanged() {
        uris = DirectoriesActivity.getFolderList(gson, DirectoriesActivity.prefKey, activity)
        super.notifyDataSetChanged()
    }
}