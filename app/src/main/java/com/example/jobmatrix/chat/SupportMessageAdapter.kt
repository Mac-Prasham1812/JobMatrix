package com.example.jobmatrix.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jobmatrix.app.R

class SupportMessageAdapter(
    private val items: List<SupportMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    inner class SentVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvMessageText)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
        val ivReadStatus: View? = view.findViewById(R.id.ivReadStatus)
    }

    inner class ReceivedVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(R.id.tvMessageText)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].sender == "user") TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_message_sent, parent, false)
            view.findViewById<View>(R.id.ivReadStatus)?.visibility = View.GONE
            SentVH(view)
        } else {
            ReceivedVH(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val timeText = if (item.createdAt > 0L) {
            android.text.format.DateFormat.format("dd MMM, hh:mm a", item.createdAt)
        } else "Recently"

        when (holder) {
            is SentVH -> {
                holder.tvText.text = item.text
                holder.tvTime.text = timeText
            }
            is ReceivedVH -> {
                holder.tvText.text = item.text
                holder.tvTime.text = timeText
            }
        }
    }

    override fun getItemCount() = items.size
}