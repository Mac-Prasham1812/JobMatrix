package com.example.jobmatrix.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ChatMessage
import com.jobmatrix.app.R

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val currentUid: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SENT = 1
        const val TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUid) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutId = if (viewType == TYPE_SENT) R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val tvText = holder.itemView.findViewById<TextView>(R.id.tvMessageText)
        val tvTime = holder.itemView.findViewById<TextView>(R.id.tvMessageTime)

        tvText.text = message.text
        tvTime.text = if (message.timestamp > 0L) {
            DateUtils.getRelativeTimeSpanString(
                message.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        } else {
            "Sending..."
        }
    }

    override fun getItemCount(): Int = messages.size
}