package com.example.jobmatrix.employer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import java.util.concurrent.TimeUnit

data class AppWithJob(val app: ApplicationModel, val job: JobModel?)

class EmployerApplicationAdapter(
    private val list: List<AppWithJob>,
    private val onItemClick: (AppWithJob) -> Unit
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        val app = item.app
        val job = item.job

        holder.tvJobTitle.text = job?.title ?: app.jobTitle
        holder.tvExperience.text = job?.experience?.ifBlank { "Fresher" } ?: "Fresher"
        holder.tvLocation.text = job?.location?.ifBlank { "N/A" } ?: "N/A"
        holder.tvAppliedDate.text = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(app.appliedAt))

        when (app.status.lowercase()) {
            "shortlisted" -> holder.tvStatus.setBackgroundResource(R.drawable.bg_status_shortlisted)
            "rejected" -> holder.tvStatus.setBackgroundResource(R.drawable.bg_status_rejected)
            else -> holder.tvStatus.setBackgroundResource(R.drawable.bg_status_applied)
        }
        holder.tvStatus.text = app.status.replaceFirstChar { it.uppercase() }

        val cached = studentCache[app.studentId]
        if (cached != null) {
            setStudentData(holder, cached.first, cached.second, cached.third)
        } else {
            db.collection("users").document(app.studentId).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: "Unknown"
                    val email = doc.getString("email") ?: "Not available"
                    val experience = doc.getString("experience")?.ifBlank { "Fresher" } ?: "Fresher"
                    studentCache[app.studentId] = Triple(name, email, experience)
                    setStudentData(holder, name, email, experience)
                }
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    private fun setStudentData(holder: VH, name: String, email: String, experience: String) {
        holder.tvStudentName.text = name
        holder.tvStudentEmail.text = email
        holder.tvExperience.text = experience
        holder.tvProfile.text = getInitials(name)
        holder.tvProfile.background.mutate().setTint(avatarColor(name))
    }

//    private fun timeAgo(time: Long): String {
//        val diff = System.currentTimeMillis() - time
//        val days = TimeUnit.MILLISECONDS.toDays(diff)
//        return when {
//            days <= 0L -> "today"
//            days == 1L -> "1 day ago"
//            else -> "$days days ago"
//        }
//    }

    private fun getInitials(name: String): String {
        val p = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            p.isEmpty() -> "?"
            p.size == 1 -> p[0].take(1).uppercase()
            else -> (p.first().take(1) + p.last().take(1)).uppercase()
        }
    }

    private fun avatarColor(seed: String): Int {
        val palette = listOf("#FF7A45", "#4C6EF5", "#12B886", "#F783AC", "#845EF7", "#FAB005")
        val idx = (seed.hashCode() and 0x7fffffff) % palette.size
        return android.graphics.Color.parseColor(palette[idx])
    }

    override fun getItemCount() = list.size
}