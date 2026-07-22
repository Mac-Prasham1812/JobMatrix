package com.example.jobmatrix.student

import android.app.AlertDialog
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.NotificationModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class NotificationAdapter(
    private val list: MutableList<NotificationModel>
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()

    class VH(view: View) : RecyclerView.ViewHolder(view) {

        val tvTitle: TextView =
            view.findViewById(R.id.tvTitle)

        val tvMessage: TextView =
            view.findViewById(R.id.tvMessage)

        val tvTime: TextView =
            view.findViewById(R.id.tvTime)

        val tvJobTitle: TextView =
            view.findViewById(R.id.tvJobTitle)

        val tvStatus: TextView =
            view.findViewById(R.id.tvStatus)

        val ivIcon: ImageView =
            view.findViewById(R.id.ivIcon)

        val card: View =
            view.findViewById(R.id.cardRoot)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_notification,
                parent,
                false
            )

        return VH(view)
    }

    override fun onBindViewHolder(
        holder: VH,
        position: Int
    ) {
        val notification = list[position]

        holder.tvTitle.text =
            notification.companyName.ifBlank {
                notification.type
            }

        holder.tvMessage.text =
            notification.message

        holder.tvJobTitle.text =
            notification.jobTitle

        holder.tvStatus.text =
            notification.type

        holder.tvTime.text =
            if (notification.createdAt > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    notification.createdAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else {
                "Recently"
            }

        val type = notification.type.lowercase()

        val iconRes = when (type) {
            "shortlisted" -> R.drawable.check_circle
            "rejected" -> R.drawable.cancel
            "applied" -> R.drawable.notification
            else -> R.drawable.notification
        }

        holder.ivIcon.setImageResource(iconRes)

        val statusColor = when (type) {
            "shortlisted" -> R.color.badge_green
            "rejected" -> R.color.red
            "applied" -> R.color.badge_orange
            else -> R.color.color_accent
        }

        holder.tvStatus.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                statusColor
            )
        )

        holder.card.setOnClickListener {
            markAsRead(notification.notificationId)
        }

        holder.card.setOnLongClickListener {
            showDeleteDialog(
                holder,
                notification,
                position
            )

            true
        }
    }

    private fun markAsRead(id: String) {
        if (id.isBlank()) return

        db.collection("notifications")
            .document(id)
            .update("isRead", true)
    }

    private fun showDeleteDialog(
        holder: VH,
        notification: NotificationModel,
        position: Int
    ) {
        val context = holder.itemView.context

        val dialogView = LayoutInflater.from(context)
            .inflate(
                R.layout.dialog_delete_notification,
                null
            )

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnCancel =
            dialogView.findViewById<TextView>(R.id.btnCancel)

        val btnDelete =
            dialogView.findViewById<TextView>(R.id.btnDelete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()

            deleteNotification(
                notification.notificationId,
                position,
                holder
            )
        }

        dialog.show()

        dialog.window?.setBackgroundDrawableResource(
            R.drawable.bg_dialog_premium
        )
    }

    private fun deleteNotification(
        id: String,
        position: Int,
        holder: VH
    ) {
        if (id.isBlank()) return

        db.collection("notifications")
            .document(id)
            .delete()
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

                Toast.makeText(
                    holder.itemView.context,
                    "Notification deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to delete notification",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun getItemCount(): Int {
        return list.size
    }
}