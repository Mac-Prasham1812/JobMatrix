package com.example.jobmatrix.student

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class NotificationAdapter(
    private val list: MutableList<ApplicationModel>
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
        val card: View = view.findViewById(R.id.cardRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = list[position]

        // Set professional icon + message
        if (app.status.equals("Shortlisted", true)) {
            holder.ivIcon.setImageResource(R.drawable.check_circle)
            holder.tvMessage.text =
                "You have been shortlisted for ${app.jobTitle} at ${app.companyName}"
        } else {
            holder.ivIcon.setImageResource(R.drawable.cancel)
            holder.tvMessage.text =
                "Your application for ${app.jobTitle} at ${app.companyName} was rejected"
        }

        // Tap → mark as read + small press animation
        holder.card.setOnClickListener {

            holder.card.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    holder.card.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                }.start()

            markAsRead(app.applicationId)
        }

        // Long press → open premium delete dialog
        holder.card.setOnLongClickListener {
            showDeleteDialog(holder, app, position)
            true
        }
    }

    private fun markAsRead(appId: String) {
        db.collection("applications")
            .document(appId)
            .update("isRead", true)
    }

    private fun showDeleteDialog(holder: VH, app: ApplicationModel, position: Int) {
        val context = holder.itemView.context
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_delete_notification, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Important → off-white premium background
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_premium)

        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDelete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            deleteNotification(app.applicationId, position, holder)
        }

        dialog.show()
    }

    private fun deleteNotification(appId: String, position: Int, holder: VH) {
        db.collection("applications")
            .document(appId)
            .delete()
            .addOnSuccessListener {

                // Premium slide + fade animation
                holder.card.animate()
                    .alpha(0f)
                    .translationX(holder.card.width.toFloat())
                    .setDuration(250)
                    .withEndAction {
                        list.removeAt(position)
                        notifyItemRemoved(position)
                    }
                    .start()

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

    override fun getItemCount(): Int = list.size
}
