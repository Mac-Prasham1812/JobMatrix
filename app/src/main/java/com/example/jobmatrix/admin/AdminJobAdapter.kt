package com.example.jobmatrix.admin

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminJobAdapter(
    private val list: MutableList<JobAdminModel>,
    private val context: Context
) : RecyclerView.Adapter<AdminJobAdapter.JobViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvJobTitle)
        val tvCompany: TextView = itemView.findViewById(R.id.tvCompany)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        val btnEdit: Button = itemView.findViewById(R.id.btnEditJob)
        val btnToggle: Button = itemView.findViewById(R.id.btnToggleStatus)
        val btnApplicants: Button = itemView.findViewById(R.id.btnViewApplicants)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = list[position]

        holder.tvTitle.text = job.title
        holder.tvCompany.text = "Company: ${job.company}"
        holder.tvCategory.text = "Category: ${job.category}"
        holder.tvStatus.text = job.status

        // Status color + button text
        if (job.status.equals("Active", true)) {
            holder.tvStatus.setTextColor(0xFF16A34A.toInt()) // Green
            holder.btnToggle.text = "Deactivate"
        } else {
            holder.tvStatus.setTextColor(0xFFEF4444.toInt()) // Red
            holder.btnToggle.text = "Activate"
        }

        // ✏️ Edit Job
        holder.btnEdit.setOnClickListener {
            val intent = Intent(context, EditJobActivity::class.java)
            intent.putExtra("jobId", job.jobId)
            context.startActivity(intent)
        }

        // 🔄 Activate / Deactivate
        holder.btnToggle.setOnClickListener {
            val newStatus = if (job.status.equals("Active", true)) "Inactive" else "Active"

            db.collection("jobs")
                .document(job.jobId)
                .update("status", newStatus)
                .addOnSuccessListener {
                    job.status = newStatus
                    notifyItemChanged(position)

                    // small bounce animation
                    holder.itemView.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(80)
                        .withEndAction {
                            holder.itemView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(80)
                                .start()
                        }.start()

                    Toast.makeText(context, "Job status updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to update status", Toast.LENGTH_SHORT).show()
                }
        }

        // 👥 View Applicants (THIS is the important part)
        holder.btnApplicants.setOnClickListener {
            val intent = Intent(context, AdminApplicationsActivity::class.java)
            intent.putExtra("jobId", job.jobId)
            intent.putExtra("jobTitle", job.title)   // extra info, useful for title
            context.startActivity(intent)
        }

        // ❌ Long press = permanent delete with fade animation
        holder.itemView.setOnLongClickListener {
            showDeleteDialog(context) {
                deleteJobAndApplications(job.jobId, position, holder)
            }
            true
        }
    }

    override fun getItemCount(): Int = list.size

    private fun showDeleteDialog(context: Context, onDelete: () -> Unit) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_admin_delete, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDelete)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.show()
    }

    private fun deleteJobAndApplications(jobId: String, position: Int, holder: JobViewHolder) {
        db.collection("applications")
            .whereEqualTo("jobId", jobId)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()

                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }

                val jobRef = db.collection("jobs").document(jobId)
                batch.delete(jobRef)

                batch.commit()
                    .addOnSuccessListener {
                        // fade out animation
                        holder.itemView.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                list.removeAt(position)
                                notifyItemRemoved(position)
                            }.start()

                        Toast.makeText(
                            context,
                            "Job and related applications deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
            }
    }
}
