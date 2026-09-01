package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

data class AppWithJob(val app: ApplicationModel, val job: JobModel?)

class EmployerApplicationAdapter(
    private val list: List<AppWithJob>,
    private val onItemClick: (AppWithJob) -> Unit
) : RecyclerView.Adapter<EmployerApplicationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()
    private val studentCache = HashMap<String, Triple<String, String, String>>()

    var selectionMode = false
        private set
    private val selectedIds = mutableSetOf<String>()

    var onSelectionChanged: ((Int) -> Unit)? = null

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentEmail: TextView = view.findViewById(R.id.tvStudentEmail)
        val tvJobTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvExperience: TextView = view.findViewById(R.id.tvExperience)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvAppliedDate: TextView = view.findViewById(R.id.tvAppliedDate)
        val tvProfile: TextView = view.findViewById(R.id.tvProfile)
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application, parent, false)
        return VH(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        val app = item.app
        val job = item.job

        holder.tvJobTitle.text = job?.title ?: app.jobTitle
        holder.tvExperience.text = job?.experience?.ifBlank { "Fresher" } ?: "Fresher"
        holder.tvLocation.text = job?.location?.ifBlank { "N/A" } ?: "N/A"
        holder.tvAppliedDate.text = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(app.appliedAt))

        when (app.status.lowercase()) {
            "shortlisted" -> {
                holder.tvStatus.setBackgroundResource(R.drawable.bg_status_shortlisted)
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
            }
            "rejected" -> {
                holder.tvStatus.setBackgroundResource(R.drawable.bg_status_rejected)
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            }
            else -> {
                holder.tvStatus.setBackgroundResource(R.drawable.bg_status_applied)
                holder.tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.color_accent))
            }
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

        if (selectionMode && holder.checkbox.visibility != View.VISIBLE) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.alpha = 0f
            holder.checkbox.scaleX = 0.7f
            holder.checkbox.scaleY = 0.7f
            holder.checkbox.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        } else if (!selectionMode) {
            holder.checkbox.visibility = View.GONE
        }
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selectedIds.contains(app.applicationId)
        holder.checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) selectedIds.add(app.applicationId) else selectedIds.remove(app.applicationId)
            onSelectionChanged?.invoke(selectedIds.size)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            } else {
                onItemClick(item)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) {
                holder.itemView.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction {
                        holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()
                selectionMode = true
                selectedIds.add(app.applicationId)
                onSelectionChanged?.invoke(selectedIds.size)
                notifyDataSetChanged()
            }
            true
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun getSelectedApplicationIds(): List<String> = selectedIds.toList()

    private fun setStudentData(holder: VH, name: String, email: String, experience: String) {
        holder.tvStudentName.text = name
        holder.tvStudentEmail.text = email
        holder.tvExperience.text = experience
        holder.tvProfile.text = getInitials(name)
        holder.tvProfile.background.mutate().setTint(avatarColor(holder.itemView.context, name))
    }

    private fun getInitials(name: String): String {
        val p = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            p.isEmpty() -> "?"
            p.size == 1 -> p[0].take(1).uppercase()
            else -> (p.first().take(1) + p.last().take(1)).uppercase()
        }
    }

    private fun avatarColor(context: android.content.Context, seed: String): Int {
        val palette = listOf(
            R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
            R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8,
            R.color.avatar_9, R.color.avatar_10
        )
        val idx = (seed.hashCode() and 0x7fffffff) % palette.size
        return androidx.core.content.ContextCompat.getColor(context, palette[idx])
    }

    override fun getItemCount() = list.size
}