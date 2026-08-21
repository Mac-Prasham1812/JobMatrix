package com.example.jobmatrix.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jobmatrix.app.R
import kotlinx.coroutines.launch

class ChatAdapter(
    private val items: MutableList<ChatListItem>,
    private val currentUid: String,
    private val applicationId: String,
    private val onLongPress: (com.example.jobmatrix.model.ChatMessage) -> Unit,
    private val onQuoteClick: (String) -> Unit,
    private val onAttachmentClick: (com.example.jobmatrix.model.ChatMessage) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SENT = 1
        const val TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is ChatListItem.Header -> TYPE_HEADER
            is ChatListItem.MessageItem -> if (item.message.senderId == currentUid) TYPE_SENT else TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutId = when (viewType) {
            TYPE_HEADER -> R.layout.item_date_header
            TYPE_SENT -> R.layout.item_message_sent
            else -> R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatListItem.Header -> {
                holder.itemView.findViewById<TextView>(R.id.tvDateHeader).text = item.label
                holder.itemView.alpha = 0f
                holder.itemView.animate().alpha(1f).setDuration(300).start()
            }
            is ChatListItem.MessageItem -> bindMessage(holder, item.message)
        }
    }

    private fun bindMessage(holder: RecyclerView.ViewHolder, message: com.example.jobmatrix.model.ChatMessage) {
        val tvText = holder.itemView.findViewById<TextView>(R.id.tvMessageText)
        val tvTime = holder.itemView.findViewById<TextView>(R.id.tvMessageTime)

        tvText.text = if (message.isDeleted) "This message was deleted" else message.text

        val timeText = if (message.timestamp > 0L) {
            android.text.format.DateFormat.format("hh:mm a", message.timestamp)
        } else "Sending..."

        val editedLabel = if (message.edited && !message.isDeleted) " • edited" else ""
        tvTime.text = "$timeText$editedLabel"

        val quoteContainer = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.quoteContainer)

        if (!message.isDeleted && message.replyToId.isNotEmpty()) {
            quoteContainer.visibility = View.VISIBLE
            holder.itemView.findViewById<TextView>(R.id.tvQuoteSender).text = message.replyToSender
            holder.itemView.findViewById<TextView>(R.id.tvQuoteText).text = message.replyToText
            quoteContainer.setOnClickListener { onQuoteClick(message.replyToId) }
        } else {
            quoteContainer.visibility = View.GONE
        }

        val ivImage = holder.itemView.findViewById<android.widget.ImageView>(R.id.ivAttachmentImage)
        val fileContainer = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.fileAttachmentContainer)

        if (!message.isDeleted && message.attachmentType == "image") {
            val shimmer = holder.itemView.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmerAttachment)
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
            ivImage.visibility = View.GONE
            fileContainer.visibility = View.GONE
            com.bumptech.glide.Glide.with(holder.itemView.context)
                .load(message.attachmentUrl)
                .signature(com.bumptech.glide.signature.ObjectKey(message.attachmentKey))
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(300))
                .placeholder(R.drawable.bg_file_attachment)
                .error(R.drawable.bg_file_attachment)
                .transform(com.bumptech.glide.load.resource.bitmap.CenterCrop(), com.bumptech.glide.load.resource.bitmap.RoundedCorners(24))
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        android.util.Log.e("JM_CHAT", "Glide failed, refreshing URL: ${e?.message}")
                        shimmer.stopShimmer(); shimmer.visibility = View.GONE; ivImage.visibility = View.VISIBLE
                        refreshAndReload(holder.itemView.context, message, ivImage, applicationId)
                        return true
                    }
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        model: Any,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        shimmer.stopShimmer(); shimmer.visibility = View.GONE; ivImage.visibility = View.VISIBLE
                        return false
                    }
                })
                .into(ivImage)
            ivImage.setOnClickListener { onAttachmentClick(message) }
        } else if (!message.isDeleted && message.attachmentType == "file") {
            ivImage.visibility = View.GONE
            fileContainer.visibility = View.VISIBLE
            holder.itemView.findViewById<TextView>(R.id.tvAttachmentName).text = message.attachmentName
            holder.itemView.findViewById<TextView>(R.id.tvAttachmentSize).text =
                String.format("%.1f MB", message.attachmentSize / 1024.0 / 1024.0)
            fileContainer.setOnClickListener { onAttachmentClick(message) }

        } else {
            ivImage.visibility = View.GONE
            fileContainer.visibility = View.GONE
        }

        tvText.visibility = if (message.text.isBlank() && message.attachmentType.isNotEmpty()) View.GONE else View.VISIBLE

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

        holder.itemView.alpha = 0f
        holder.itemView.animate().alpha(1f).setDuration(200).start()

        holder.itemView.setOnLongClickListener {
            if (!message.isDeleted) {
                val row = holder.itemView
                val original = row.background
                row.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                    androidx.core.content.ContextCompat.getColor(row.context, R.color.color_accent), 60
                ))
                row.postDelayed({ row.background = original }, 250)
                onLongPress(message)
            }
            true
        }
    }

    private fun refreshAndReload(context: android.content.Context, message: com.example.jobmatrix.model.ChatMessage, ivImage: android.widget.ImageView, applicationId: String) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
            val token = "Bearer " + result.token
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val response = com.example.jobmatrix.network.RetrofitClient.api
                        .getChatAttachmentUrl(token, message.attachmentKey)
                    if (response.isSuccessful && response.body() != null) {
                        val freshUrl = response.body()!!.url
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            com.bumptech.glide.Glide.with(context).load(freshUrl).into(ivImage)
                        }
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("chats").document(applicationId)
                            .collection("messages").document(message.messageId)
                            .update("attachmentUrl", freshUrl)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JM_CHAT", "Refresh URL failed", e)
                }
            }
        }
    }
    override fun getItemCount(): Int = items.size
}