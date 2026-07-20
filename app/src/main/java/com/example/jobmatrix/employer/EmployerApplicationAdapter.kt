package com.example.jobmatrix.employer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import java.util.concurrent.TimeUnit

data class AppWithJob(val app: ApplicationModel, val job: JobModel?)

class EmployerApplicationAdapter(
    private val list: List<AppWithJob>,
    private val onResumeClick: (String) -> Unit
) : RecyclerView.Adapter<EmployerApplicationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()
    private val studentCache = HashMap<String, Triple<String, String, String>>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentEmail: TextView = view.findViewById(R.id.tvStudentEmail)
        val tvJobTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvExperience: TextView = view.findViewById(R.id.tvExperience)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvAppliedDate: TextView = view.findViewById(R.id.tvAppliedDate)
        val tvProfile: TextView = view.findViewById(R.id.tvProfile)
        val btnShortlist: Button = view.findViewById(R.id.btnShortlist)
        val btnReject: Button = view.findViewById(R.id.btnReject)
        val btnViewResume: Button = view.findViewById(R.id.btnViewResume)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (app, job) = list[position]

        holder.tvJobTitle.text = job?.title ?: app.jobTitle
        holder.tvExperience.text = job?.experience?.ifBlank { "N/A" } ?: "N/A"
        holder.tvLocation.text = job?.location?.ifBlank { "N/A" } ?: "N/A"
        holder.tvAppliedDate.text = "Applied ${timeAgo(app.appliedAt)}"

        when (app.status.lowercase()) {
            "shortlisted" -> holder.tvStatus.setBackgroundResource(R.drawable.bg_status_shortlisted)
            "rejected" -> holder.tvStatus.setBackgroundResource(R.drawable.bg_status_rejected)
            else -> holder.tvStatus.setBackgroundResource(R.drawable.bg_status_applied)
        }
        holder.tvStatus.text = app.status.replaceFirstChar { it.uppercase() }

        if (studentCache.containsKey(app.studentId)) {
            val (name, email, _) = studentCache[app.studentId]!!
            setStudentData(holder, name, email)
        } else {
            db.collection("users").document(app.studentId).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: "Unknown"
                    val email = doc.getString("email") ?: "Not available"
                    studentCache[app.studentId] = Triple(name, email, "")
                    setStudentData(holder, name, email)
                }
        }

        holder.btnViewResume.setOnClickListener {
            if (app.resumeLink.isNotEmpty()) onResumeClick(app.resumeLink)
        }
        holder.btnShortlist.setOnClickListener { updateStatus(app, "Shortlisted", holder) }
        holder.btnReject.setOnClickListener { updateStatus(app, "Rejected", holder) }
    }

    private fun setStudentData(holder: VH, name: String, email: String) {
        holder.tvStudentName.text = name
        holder.tvStudentEmail.text = email
        holder.tvProfile.text = if (name.isNotEmpty()) name.first().uppercaseChar().toString() else "?"
    }

    private fun timeAgo(time: Long): String {
        val diff = System.currentTimeMillis() - time
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            days <= 0L -> "today"
            days == 1L -> "1 day ago"
            else -> "$days days ago"
        }
    }

    private fun updateStatus(app: ApplicationModel, newStatus: String, holder: VH) {
        val message = when (newStatus) {
            "Shortlisted" -> "🎉 You have been shortlisted for further rounds."
            "Rejected" -> "❌ Your application was not selected."
            else -> ""
        }
        db.collection("applications").document(app.applicationId)
            .update(mapOf("status" to newStatus, "hasNotification" to true, "notificationMessage" to message, "isRead" to false))
            .addOnSuccessListener {
                Toast.makeText(holder.itemView.context, "Status updated & student notified", Toast.LENGTH_SHORT).show()
            }
    }

    override fun getItemCount() = list.size
}