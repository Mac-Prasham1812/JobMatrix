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
    private val currentUid: String,
    private val onLongPress: (ChatMessage) -> Unit
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

        tvText.text = if (message.isDeleted) "This message was deleted" else message.text

        val timeText = if (message.timestamp > 0L) {
            DateUtils.getRelativeTimeSpanString(message.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        } else "Sending..."

        val editedLabel = if (message.edited && !message.isDeleted) " • edited" else ""
        tvTime.text = "$timeText$editedLabel"

        if (message.senderId == currentUid) {
            val ivTick = holder.itemView.findViewById<android.widget.ImageView>(R.id.ivReadStatus)
            if (message.isDeleted) {
                ivTick.visibility = View.GONE
            } else {
                ivTick.visibility = View.VISIBLE
                if (message.isRead) {
                    ivTick.setImageResource(R.drawable.ic_tick_double)
                    ivTick.setColorFilter(android.graphics.Color.parseColor("#4FC3F7"))
                } else {
                    ivTick.setImageResource(R.drawable.ic_tick_single)
                    ivTick.clearColorFilter()
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            if (message.senderId == currentUid && !message.isDeleted) onLongPress(message)
            true
        }
    }

    override fun getItemCount(): Int = messages.size
}