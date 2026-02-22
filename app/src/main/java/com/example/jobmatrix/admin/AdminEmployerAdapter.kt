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

class AdminEmployerAdapter(
    private val list: MutableList<EmployerModel>
) : RecyclerView.Adapter<AdminEmployerAdapter.EmployerViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class EmployerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvEmployerName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvEmployerEmail)
        val tvCompany: TextView = itemView.findViewById(R.id.tvCompanyName)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteEmployer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_employer, parent, false)
        return EmployerViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmployerViewHolder, position: Int) {
        val employer = list[position]

        holder.tvName.text = employer.name
        holder.tvEmail.text = employer.email
        holder.tvCompany.text = employer.companyName

        holder.btnDelete.setOnClickListener {
            showDeleteDialog(holder.itemView.context) {
                deleteEmployer(employer.userId, position, holder)
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

        tvTitle.text = "Delete Employer"
        tvMessage.text =
            "This will permanently delete the employer, all jobs posted by them, and all related applications."

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.show()
    }

    // 🔥 Delete Employer + Their Jobs + Their Applications
    private fun deleteEmployer(employerId: String, position: Int, holder: EmployerViewHolder) {

        db.collection("jobs")
            .whereEqualTo("employerId", employerId)
            .get()
            .addOnSuccessListener { jobsSnapshot ->

                val batch = db.batch()
                val jobIds = ArrayList<String>()

                for (jobDoc in jobsSnapshot.documents) {
                    jobIds.add(jobDoc.id)
                    batch.delete(jobDoc.reference)
                }

                if (jobIds.isNotEmpty()) {
                    db.collection("applications")
                        .whereIn("jobId", jobIds)
                        .get()
                        .addOnSuccessListener { appsSnapshot ->
                            for (appDoc in appsSnapshot.documents) {
                                batch.delete(appDoc.reference)
                            }

                            val userRef = db.collection("users").document(employerId)
                            batch.delete(userRef)

                            commitBatch(batch, position, holder)
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                holder.itemView.context,
                                "Failed to delete applications",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    val userRef = db.collection("users").document(employerId)
                    batch.delete(userRef)
                    commitBatch(batch, position, holder)
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to fetch employer jobs",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun commitBatch(
        batch: com.google.firebase.firestore.WriteBatch,
        position: Int,
        holder: EmployerViewHolder
    ) {
        batch.commit()
            .addOnSuccessListener {
                list.removeAt(position)
                notifyItemRemoved(position)

                Toast.makeText(
                    holder.itemView.context,
                    "Employer and all related data deleted successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    holder.itemView.context,
                    "Delete failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun getItemCount(): Int = list.size
}
