package com.example.jobmatrix.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jobmatrix.app.R

data class ChatPreviewItem(
    val applicationId: String = "",
    val otherPersonName: String = "",
    val jobTitle: String = "",
    val lastMessage: String = "",
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val avatarInitial: String = "",
    val avatarColor: Int = 0
)

class ChatListAdapter(
    private val items: MutableList<ChatPreviewItem>,
    private val onClick: (ChatPreviewItem) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvJobTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvLastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvUnreadBadge: TextView = view.findViewById(R.id.tvUnreadBadge)
        val dotOnline: View = view.findViewById(R.id.dotOnline)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_list, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvAvatar.text = item.avatarInitial
        holder.tvAvatar.background.setTint(item.avatarColor)
        holder.tvName.text = item.otherPersonName
        holder.tvJobTitle.text = item.jobTitle
        holder.tvLastMessage.text = item.lastMessage.ifBlank { "No messages yet" }
        holder.tvTime.text = formatTime(item.lastMessageAt)
        holder.dotOnline.visibility = if (item.isOnline) View.VISIBLE else View.GONE

        if (item.unreadCount > 0) {
            holder.tvUnreadBadge.visibility = View.VISIBLE
            holder.tvUnreadBadge.text = if (item.unreadCount > 9) "9+" else item.unreadCount.toString()
            holder.tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.tvLastMessage.setTextColor(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.color_text_primary)
            )
        } else {
            holder.tvUnreadBadge.visibility = View.GONE
            holder.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.tvLastMessage.setTextColor(
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.color_text_secondary)
            )
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    private fun formatTime(ts: Long): String {
        if (ts == 0L) return ""
        val diff = System.currentTimeMillis() - ts
        val min = diff / 60000
        return when {
            min < 1 -> "now"
            min < 60 -> "${min}m"
            min < 1440 -> "${min / 60}h"
            min < 2880 -> "Yesterday"
            else -> android.text.format.DateFormat.format("dd MMM", ts).toString()
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ChatPreviewItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}