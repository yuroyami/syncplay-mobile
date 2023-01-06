package app.controllers.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.R
import app.ui.activities.RoomActivity

class SharedPlaylistRecycAdapter(private val act: RoomActivity, var list: MutableList<String>) :
    RecyclerView.Adapter<SharedPlaylistRecycAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_playlist_line, parent, false)
        return ViewHolder(view, act)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View, activity: RoomActivity) : RecyclerView.ViewHolder(itemView) {
        val line: LinearLayout = itemView.findViewById(R.id.line)
        val playStatus: ImageView = itemView.findViewById(R.id.play_status)
        val name: TextView = itemView.findViewById(R.id.name)

        init {
            line.setOnClickListener {
                val popup = PopupMenu(it.context, it)
                val playitem = popup.menu.add(0, 0, 0, it.context.getString(R.string.play))
                val deleteitem = popup.menu.add(0, 1, 0, it.context.getString(R.string.delete))
                popup.setOnMenuItemClickListener { it2 ->
                    when (it2) {
                        playitem -> activity //.sendPlaylistSelection(bindingAdapterPosition)
                        deleteitem -> activity// .deleteItemFromPlaylist(bindingAdapterPosition)
                    }
                    return@setOnMenuItemClickListener true
                }
                popup.show()
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = act.p.session.sharedPlaylist[holder.absoluteAdapterPosition]
        holder.name.text = text
        if (act.p.session.sharedPlaylist.indexOf(text) == act.p.session.sharedPlaylistIndex) {
            holder.playStatus.visibility = View.VISIBLE
        } else {
            holder.playStatus.visibility = View.GONE
        }
    }
}