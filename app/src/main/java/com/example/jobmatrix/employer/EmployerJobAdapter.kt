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
        val tvCompany: TextView = view.findViewById(R.id.tvCompany)
        val tvJobLocation: TextView = view.findViewById(R.id.tvJobLocation)

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
        holder.tvCompany.text = job.company.ifBlank { "Company not specified" }
        holder.tvJobLocation.text = job.location.ifBlank { "Location not specified" }
        holder.tvSalary.text = if (job.salary.isBlank()) "₹0" else "₹${job.salary}"

        holder.tvApplicants.text = String.format("%02d Applicants", job.applicantsCount)
        holder.vAccent.setBackgroundColor(jobAccentColor(holder.itemView.context, position))
        val status = job.status.ifBlank { "Active" }
        holder.tvStatus.text = status.replaceFirstChar { it.uppercase() }

        if (status.equals("Inactive", ignoreCase = true)) {
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_inactive)
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
        } else {
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_active)
            holder.tvStatus.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    holder.itemView.context,
                    R.color.color_accent
                )
            )
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, EmployerApplicationsActivity::class.java)
            intent.putExtra("jobId", job.jobId)
            it.context.startActivity(intent)
        }

        holder.ivMoreContainer.setOnClickListener {
            val ctx = it.context
            val popupView = LayoutInflater.from(ctx).inflate(R.layout.popup_job_menu, null)
            val popupWindow = android.widget.PopupWindow(
                popupView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            holder.ivMoreContainer.animate().rotation(180f).setDuration(200).start()
            popupWindow.setOnDismissListener {
                holder.ivMoreContainer.animate().rotation(0f).setDuration(200).start()
            }

            val isInactive = job.status.equals("Inactive", ignoreCase = true)
            val menuToggle = popupView.findViewById<TextView>(R.id.menuDeactivateJob)
            menuToggle.text = if (isInactive) "Activate Job" else "Deactivate Job"
            menuToggle.setCompoundDrawablesWithIntrinsicBounds(
                if (isInactive) R.drawable.ic_check else R.drawable.ic_deactivate, 0, 0, 0
            )

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
            menuToggle.setOnClickListener {
                val newStatus = if (isInactive) "Active" else "Inactive"
                val updates = if (isInactive)
                    mapOf("status" to newStatus)
                else
                    mapOf("status" to newStatus, "deactivatedAt" to System.currentTimeMillis())
                db.collection("jobs").document(job.jobId).update(updates)
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
                            if (pos != RecyclerView.NO_POSITION) {
                                list.removeAt(pos); notifyItemRemoved(pos)
                            }
                            Toast.makeText(ctx, "Job deleted permanently", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                popupWindow.dismiss()
            }

            popupWindow.animationStyle = R.style.PopupAnimation

            popupView.measure(
                android.view.View.MeasureSpec.UNSPECIFIED,
                android.view.View.MeasureSpec.UNSPECIFIED
            )
            val popupHeight = popupView.measuredHeight

            val location = IntArray(2)
            it.getLocationOnScreen(location)
            val screenHeight = ctx.resources.displayMetrics.heightPixels
            val spaceBelow = screenHeight - (location[1] + it.height)

            if (spaceBelow < popupHeight) {
                popupWindow.showAsDropDown(it, -180, -(popupHeight + it.height))
            } else {
                popupWindow.showAsDropDown(it, -180, 0)
            }
        }
    }
    private fun jobAccentColor(context: android.content.Context, position: Int): Int {
        val palette = listOf(
            R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_5,
            R.color.avatar_6, R.color.avatar_7, R.color.avatar_8, R.color.avatar_9, R.color.avatar_10
        )
        val idx = position % palette.size
        return androidx.core.content.ContextCompat.getColor(context, palette[idx])
    }

    override fun getItemCount(): Int = list.size
}