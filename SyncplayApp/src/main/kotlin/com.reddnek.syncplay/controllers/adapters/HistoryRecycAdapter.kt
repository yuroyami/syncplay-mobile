package com.reddnek.syncplay.controllers.adapters

import android.content.Context
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.reddnek.syncplay.R
import com.reddnek.syncplay.wrappers.Message

class HistoryRecycAdapter(var list: MutableList<Message>, val context: Context) :
    RecyclerView.Adapter<HistoryRecycAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.popup_messages_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txt: TextView = itemView.findViewById(R.id.msg_txt)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = list[holder.absoluteAdapterPosition].factorize(true, context)
        holder.txt.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(text)
    }
}