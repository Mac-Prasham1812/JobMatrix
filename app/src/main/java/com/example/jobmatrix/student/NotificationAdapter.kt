package com.example.jobmatrix.student

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.NotificationModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class NotificationAdapter(
    private val list: MutableList<NotificationModel>
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()

    private var lastAnimatedPosition = -1

    private val avatarColors = listOf(
        "#7C6FF0", "#35C1B0", "#F5A623", "#E85D75", "#4E8CFF", "#9B6FD6"
    )

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvAvatarLetter: TextView = view.findViewById(R.id.tvAvatarLetter)
        val avatarContainer: CardView = view.findViewById(R.id.avatarContainer)
        val unreadDot: View = view.findViewById(R.id.unreadDot)
        val card: View = view.findViewById(R.id.cardRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, @SuppressLint("RecyclerView") position: Int) {
        val notification = list[position]
        val label = notification.companyName.ifBlank { notification.type }

        holder.tvTitle.text = label
        holder.tvMessage.text = notification.message

        holder.tvTime.text = if (notification.createdAt > 0L) {
            DateUtils.getRelativeTimeSpanString(
                notification.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        } else {
            "Recently"
        }

        holder.unreadDot.visibility = if (notification.isRead) View.GONE else View.VISIBLE

        val avatarLetter = label.trim().firstOrNull()?.uppercaseChar() ?: '?'
        holder.tvAvatarLetter.text = avatarLetter.toString()

        val colorIndex = Math.abs(label.hashCode()) % avatarColors.size
        holder.avatarContainer.setCardBackgroundColor(Color.parseColor(avatarColors[colorIndex]))

        val type = notification.type.lowercase()

        if (type == "shortlisted" || type == "rejected" || type == "applied") {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = notification.type

            val statusColor = when (type) {
                "shortlisted" -> R.color.badge_green
                "rejected" -> R.color.red
                else -> R.color.badge_orange
            }

            holder.tvStatus.background.mutate().setTint(
                ContextCompat.getColor(holder.itemView.context, statusColor)
            )
        } else {
            holder.tvStatus.visibility = View.GONE
        }

        holder.card.setOnClickListener {
            holder.unreadDot.visibility = View.GONE
            markAsRead(notification.notificationId, holder.itemView.context)

            if (type == "message") {
                val intent = android.content.Intent(
                    holder.itemView.context,
                    com.example.jobmatrix.chat.ChatActivity::class.java
                )
                intent.putExtra("applicationId", notification.applicationId)
                holder.itemView.context.startActivity(intent)
            }
        }

        holder.card.setOnLongClickListener {
            showDeleteDialog(holder, notification, position)
            true
        }

        if (position > lastAnimatedPosition) {
            holder.itemView.startAnimation(
                AnimationUtils.loadAnimation(holder.itemView.context, R.anim.item_fade_slide)
            )
            lastAnimatedPosition = position
        }
    }

    private fun markAsRead(id: String, context: android.content.Context) {
        if (id.isBlank()) return
        db.collection("notifications").document(id).update("isRead", true)
            .addOnFailureListener {
                android.util.Log.e("NotificationAdapter", "markAsRead failed: ${it.message}")
                android.widget.Toast.makeText(context, "Mark read failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    private fun showDeleteDialog(holder: VH, notification: NotificationModel, position: Int) {
        val context = holder.itemView.context
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_delete_notification, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDelete)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            deleteNotification(notification.notificationId, position, holder)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_premium)
    }

    private fun deleteNotification(id: String, position: Int, holder: VH) {
        if (id.isBlank()) return

        db.collection("notifications").document(id).delete()
            .addOnSuccessListener {
                if (position >= 0 && position < list.size) {
                    holder.card.animate()
                        .alpha(0f)
                        .translationX(holder.card.width.toFloat())
                        .setDuration(250)
                        .withEndAction {
                            list.removeAt(position)
                            notifyItemRemoved(position)
                        }
                        .start()
                }
                Toast.makeText(holder.itemView.context, "Notification deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(holder.itemView.context, "Failed to delete notification", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getItemCount(): Int = list.size

    fun resetAnimation() {
        lastAnimatedPosition = -1
    }
}