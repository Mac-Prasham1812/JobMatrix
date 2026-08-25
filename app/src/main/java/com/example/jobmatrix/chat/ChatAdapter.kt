package com.example.jobmatrix.chat

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.facebook.shimmer.ShimmerFrameLayout
import com.jobmatrix.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val loadingImages = mutableSetOf<String>() // Track in-flight loads

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
                if (holder.itemView.alpha == 0f) {
                    holder.itemView.animate().alpha(1f).setDuration(300).start()
                }
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

        // Quote section
        val quoteContainer = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.quoteContainer)
        if (!message.isDeleted && message.replyToId.isNotEmpty()) {
            quoteContainer.visibility = View.VISIBLE
            holder.itemView.findViewById<TextView>(R.id.tvQuoteSender).text = message.replyToSender
            holder.itemView.findViewById<TextView>(R.id.tvQuoteText).text = message.replyToText

            val ivQuoteThumb = holder.itemView.findViewById<ImageView>(R.id.ivQuoteThumb)
            if (message.replyToAttachmentType == "image" && message.replyToAttachmentUrl.isNotEmpty()) {
                ivQuoteThumb.visibility = View.VISIBLE
                Glide.with(holder.itemView.context)
                    .load(message.replyToAttachmentUrl)
                    .transform(com.bumptech.glide.load.resource.bitmap.CenterCrop(), com.bumptech.glide.load.resource.bitmap.RoundedCorners(12))
                    .into(ivQuoteThumb)
            } else if (message.replyToAttachmentType == "file") {
                ivQuoteThumb.visibility = View.VISIBLE
                ivQuoteThumb.setImageResource(R.drawable.ic_file)
                ivQuoteThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivQuoteThumb.setPadding(6, 6, 6, 6)
            } else {
                ivQuoteThumb.visibility = View.GONE
            }

            quoteContainer.setOnClickListener { onQuoteClick(message.replyToId) }
            ivQuoteThumb.setOnClickListener { onQuoteClick(message.replyToId) }
        } else {
            quoteContainer.visibility = View.GONE
        }

        // Attachment section - ONLY bind if not already loading
        if (!message.isDeleted && message.attachmentType == "image") {
            bindImageAttachment(holder, message)
        } else if (!message.isDeleted && message.attachmentType == "file") {
            bindFileAttachment(holder, message)
        } else {
            clearAttachments(holder)
        }

        tvText.visibility = if (message.text.isBlank() && message.attachmentType.isNotEmpty()) View.GONE else View.VISIBLE

        // Read status (sent side only)
        if (message.senderId == currentUid) {
            val ivTick = holder.itemView.findViewById<ImageView>(R.id.ivReadStatus)
            if (message.isDeleted) {
                ivTick.visibility = View.GONE
            } else {
                ivTick.visibility = View.VISIBLE
                ivTick.setImageResource(if (message.isRead) R.drawable.ic_tick_double else R.drawable.ic_tick_single)
                if (message.isRead) {
                    ivTick.setColorFilter(android.graphics.Color.parseColor("#4FC3F7"))
                } else {
                    ivTick.clearColorFilter()
                }
            }
        }

        // Animate in only if alpha was 0
        if (holder.itemView.alpha == 0f) {
            holder.itemView.animate().alpha(1f).setDuration(200).start()
        }

        // Long press
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

    private fun bindImageAttachment(holder: RecyclerView.ViewHolder, message: com.example.jobmatrix.model.ChatMessage) {
        val ivImage = holder.itemView.findViewById<ImageView>(R.id.ivAttachmentImage)
        val shimmer = holder.itemView.findViewById<ShimmerFrameLayout>(R.id.shimmerAttachment)
        val imageContainer = holder.itemView.findViewById<android.widget.FrameLayout>(R.id.attachmentImageContainer)

        // Hide file/text, show image wrapper
        holder.itemView.findViewById<android.widget.LinearLayout>(R.id.fileAttachmentContainer).visibility = View.GONE
        holder.itemView.findViewById<android.widget.FrameLayout>(R.id.imageWrapper).visibility = View.VISIBLE

        val msgId = message.messageId
        if (loadingImages.contains(msgId)) {
            // Already loading, don't restart
            return
        }

        loadingImages.add(msgId)
        shimmer.visibility = View.VISIBLE
        shimmer.startShimmer()
        imageContainer.visibility = View.INVISIBLE

        Glide.with(holder.itemView.context)
            .load(message.attachmentUrl)
            .signature(com.bumptech.glide.signature.ObjectKey(message.attachmentKey))
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .placeholder(R.drawable.bg_file_attachment)
            .error(R.drawable.bg_file_attachment)
            .transform(
                com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                com.bumptech.glide.load.resource.bitmap.RoundedCorners(24)
            )
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (loadingImages.remove(msgId)) {
                        // Only refresh once per message
                        refreshAndReload(holder.itemView.context, message, ivImage, applicationId) {
                            shimmer.stopShimmer()
                            shimmer.visibility = View.GONE
                            imageContainer.visibility = View.VISIBLE
                        }
                    }
                    return true
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    loadingImages.remove(msgId)
                    shimmer.stopShimmer()
                    shimmer.visibility = View.GONE
                    imageContainer.visibility = View.VISIBLE
                    return false
                }
            })
            .into(ivImage)

        ivImage.setOnClickListener { onAttachmentClick(message) }
    }

    private fun bindFileAttachment(holder: RecyclerView.ViewHolder, message: com.example.jobmatrix.model.ChatMessage) {
        // Hide image/shimmer, show file
        holder.itemView.findViewById<android.widget.FrameLayout>(R.id.imageWrapper).visibility = View.GONE
        val fileContainer = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.fileAttachmentContainer)
        fileContainer.visibility = View.VISIBLE

        holder.itemView.findViewById<TextView>(R.id.tvAttachmentName).text = message.attachmentName
        holder.itemView.findViewById<TextView>(R.id.tvAttachmentSize).text =
            String.format("%.1f MB", message.attachmentSize / 1024.0 / 1024.0)
        fileContainer.setOnClickListener { onAttachmentClick(message) }
    }

    private fun clearAttachments(holder: RecyclerView.ViewHolder) {
        holder.itemView.findViewById<android.widget.FrameLayout>(R.id.imageWrapper).visibility = View.GONE
        holder.itemView.findViewById<android.widget.LinearLayout>(R.id.fileAttachmentContainer).visibility = View.GONE
    }

    private fun refreshAndReload(
        context: android.content.Context,
        message: com.example.jobmatrix.model.ChatMessage,
        ivImage: ImageView,
        applicationId: String,
        onComplete: () -> Unit
    ) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
            val token = "Bearer " + result.token
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = com.example.jobmatrix.network.RetrofitClient.api
                        .getChatAttachmentUrl(token, message.attachmentKey)
                    if (response.isSuccessful && response.body() != null) {
                        val freshUrl = response.body()!!.url

                        // Update Firestore
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("chats").document(applicationId)
                            .collection("messages").document(message.messageId)
                            .update("attachmentUrl", freshUrl)

                        withContext(Dispatchers.Main) {
                            Glide.with(context)
                                .load(freshUrl)
                                .transform(
                                    com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                                    com.bumptech.glide.load.resource.bitmap.RoundedCorners(24)
                                )
                                .placeholder(R.drawable.bg_file_attachment)
                                .error(R.drawable.bg_file_attachment)
                                .into(ivImage)
                            onComplete()
                        }
                    } else {
                        withContext(Dispatchers.Main) { onComplete() }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JM_CHAT", "Refresh URL failed", e)
                    withContext(Dispatchers.Main) { onComplete() }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size
}