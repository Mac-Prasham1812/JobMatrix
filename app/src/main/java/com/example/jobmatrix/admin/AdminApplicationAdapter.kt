package com.example.jobmatrix.admin

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminApplicationAdapter(
    private val list: MutableList<ApplicationAdminModel>
) : RecyclerView.Adapter<AdminApplicationAdapter.AppViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvJobTitle: TextView = itemView.findViewById(R.id.tvJobTitle)
        val tvCompany: TextView = itemView.findViewById(R.id.tvCompany)
        val tvStudent: TextView = itemView.findViewById(R.id.tvStudent)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteApplication)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_application, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = list[position]

        holder.tvJobTitle.text = app.jobTitle
        holder.tvCompany.text = "Company: ${app.companyName}"
        holder.tvStatus.text = app.status

        // Safety check
        if (app.studentId.isEmpty() || app.studentId == "users") {
            holder.tvStudent.text = "Student: Invalid ID"
            return
        }

        db.collection("users")
            .document(app.studentId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val studentName = doc.getString("name") ?: "Unknown Student"
                    holder.tvStudent.text = "Student: $studentName"
                } else {
                    holder.tvStudent.text = "Student: Not Found"
                }
            }
            .addOnFailureListener {
                holder.tvStudent.text = "Student: Error"
            }

        holder.btnDelete.setOnClickListener {
            showDeleteDialog(holder.itemView.context) {
                deleteApplication(app.applicationId, position, holder)
            }
        }
    }

    private fun showDeleteDialog(context: Context, onDelete: () -> Unit) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_admin_delete, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDelete)

        tvTitle.text = "Delete Application"
        tvMessage.text = "This will permanently delete this application. This action cannot be undone."

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.show()
    }

    private fun deleteApplication(appId: String, position: Int, holder: AppViewHolder) {
        db.collection("applications").document(appId)
            .delete()
            .addOnSuccessListener {
                list.removeAt(position)
                notifyItemRemoved(position)

                Toast.makeText(
                    holder.itemView.context,
                    "Application deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to delete",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun getItemCount(): Int = list.size
}
