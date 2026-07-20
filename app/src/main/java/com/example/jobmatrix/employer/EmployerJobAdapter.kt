package com.example.jobmatrix.employer

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        val tvApplicants: TextView = view.findViewById(R.id.tvApplicants)
        val ivMoreContainer: View = view.findViewById(R.id.ivMoreContainer)
        val vAccent: View = view.findViewById(R.id.vAccent)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_employer_job, parent, false)
        return JobVH(view)
    }

    override fun onBindViewHolder(holder: JobVH, position: Int) {
        val job = list[position]

        holder.tvTitle.text = job.title.ifBlank { "Untitled Job" }
        holder.tvCategory.text = job.category.ifBlank { "General" }
        holder.tvSalary.text = if (job.salary.isBlank()) "₹0" else "₹${job.salary}"

        holder.tvApplicants.text = String.format("%02d Applicants", job.applicantsCount)

        val status = job.status.ifBlank { "Active" }
        holder.tvStatus.text = status.replaceFirstChar { it.uppercase() }

        if (status.equals("Inactive", ignoreCase = true)) {
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_inactive)
            holder.tvStatus.setTextColor(android.graphics.Color.WHITE)
        } else {
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_active)
            holder.tvStatus.setTextColor(android.graphics.Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, EmployerApplicationsActivity::class.java)
            intent.putExtra("jobId", job.jobId)
            it.context.startActivity(intent)
        }

        holder.ivMoreContainer.setOnClickListener {
            val ctx = it.context
            val popupView = LayoutInflater.from(ctx).inflate(R.layout.popup_job_menu, null)
            val popupWindow = android.widget.PopupWindow(popupView, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true)

            holder.ivMoreContainer.animate().rotation(180f).setDuration(200).start()
            popupWindow.setOnDismissListener {
                holder.ivMoreContainer.animate().rotation(0f).setDuration(200).start()
            }

            popupView.findViewById<TextView>(R.id.menuViewApplicants).setOnClickListener {
                val intent = Intent(ctx, EmployerApplicationsActivity::class.java)
                intent.putExtra("jobId", job.jobId)
                ctx.startActivity(intent)
                popupWindow.dismiss()
            }
            popupView.findViewById<TextView>(R.id.menuEditJob).setOnClickListener {
                val intent = Intent(ctx, EditJobActivity::class.java)
                intent.putExtra("jobId", job.jobId)
                ctx.startActivity(intent)
                popupWindow.dismiss()
            }
            popupView.findViewById<TextView>(R.id.menuDeactivateJob).setOnClickListener {
                db.collection("jobs").document(job.jobId)
                    .update(mapOf("status" to "Inactive", "deactivatedAt" to System.currentTimeMillis()))
                popupWindow.dismiss()
            }
            popupView.findViewById<TextView>(R.id.menuDeleteJob).setOnClickListener {
                db.collection("applications").whereEqualTo("jobId", job.jobId).get()
                    .addOnSuccessListener { snapshot ->
                        val batch = db.batch()
                        for (doc in snapshot.documents) batch.delete(doc.reference)
                        batch.delete(db.collection("jobs").document(job.jobId))
                        batch.commit().addOnSuccessListener {
                            val pos = holder.adapterPosition
                            if (pos != RecyclerView.NO_POSITION) { list.removeAt(pos); notifyItemRemoved(pos) }
                            Toast.makeText(ctx, "Job deleted permanently", Toast.LENGTH_SHORT).show()
                        }
                    }
                popupWindow.dismiss()
            }

            popupWindow.animationStyle = R.style.PopupAnimation
            popupWindow.showAsDropDown(it, -180, 0)
        }

        val accentColors = intArrayOf(
            android.graphics.Color.parseColor("#2563EB"),
            android.graphics.Color.parseColor("#F59E0B"),
            android.graphics.Color.parseColor("#16A34A"),
            android.graphics.Color.parseColor("#7C3AED")
        )
        holder.vAccent.setBackgroundColor(accentColors[position % accentColors.size])
    }

    override fun getItemCount(): Int = list.size
}