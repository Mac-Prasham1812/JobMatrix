package com.example.jobmatrix.employer

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EmployerJobAdapter(
    private val list: MutableList<JobModel>
) : RecyclerView.Adapter<EmployerJobAdapter.JobVH>() {

    private val db = FirebaseFirestore.getInstance()

    inner class JobVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvSalary: TextView = view.findViewById(R.id.tvSalary)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnEdit: Button = view.findViewById(R.id.btnEditJob)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteJob)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_employer_job, parent, false)
        return JobVH(view)
    }

    override fun onBindViewHolder(holder: JobVH, position: Int) {

        val job = list[position]

        // Job data binding
        holder.tvTitle.text = job.title
        holder.tvCategory.text = "Category: ${job.category}"
        holder.tvSalary.text = "₹${job.salary}"


        holder.tvStatus.text = "ACTIVE"
        holder.tvStatus.setBackgroundResource(R.drawable.bg_status_active)

        // Open Applications screen
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, EmployerApplicationsActivity::class.java)
            intent.putExtra("jobId", job.jobId)
            it.context.startActivity(intent)
        }

        // EDIT JOB
        holder.btnEdit.setOnClickListener {
            val intent = Intent(holder.itemView.context, EditJobActivity::class.java)
            intent.putExtra("jobId", job.jobId)
            holder.itemView.context.startActivity(intent)
        }

        // DELETE JOB
        holder.btnDelete.setOnClickListener {

            val context = holder.itemView.context

            db.collection("applications")
                .whereEqualTo("jobId", job.jobId)
                .get()
                .addOnSuccessListener { snapshot ->

                    val batch = db.batch()

                    // Delete all applications
                    for (doc in snapshot.documents) {
                        batch.delete(doc.reference)
                    }

                    val jobRef = db.collection("jobs").document(job.jobId)
                    batch.delete(jobRef)

                    batch.commit()
                        .addOnSuccessListener {

                            // Remove from UI
                            val pos = holder.adapterPosition
                            if (pos != RecyclerView.NO_POSITION) {
                                list.removeAt(pos)
                                notifyItemRemoved(pos)
                            }

                            Toast.makeText(
                                context,
                                "Job deleted permanently",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                context,
                                "Failed to delete job",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        context,
                        "Failed to fetch job applications",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    override fun getItemCount(): Int = list.size
}
