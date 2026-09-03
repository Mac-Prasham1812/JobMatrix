package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

data class AppWithJob(val app: ApplicationModel, val job: JobModel?)

class EmployerApplicationAdapter(
    private val list: List<AppWithJob>,
    private val onItemClick: (AppWithJob) -> Unit,
    private val onStatusPillClick: (AppWithJob) -> Unit
) : RecyclerView.Adapter<EmployerApplicationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()
    private val studentCache = HashMap<String, Triple<String, String, String>>()

    var selectionMode = false
        private set
    private val selectedIds = mutableSetOf<String>()

    var onSelectionChanged: ((Int) -> Unit)? = null

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.cardRoot)
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentEmail: TextView = view.findViewById(R.id.tvStudentEmail)
        val tvJobTitle: TextView = view.findViewById(R.id.tvJobTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvExperience: TextView = view.findViewById(R.id.tvExperience)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvAppliedDate: TextView = view.findViewById(R.id.tvAppliedDate)
        val tvProfile: TextView = view.findViewById(R.id.tvProfile)
        val ivSelectedBadge: ImageView = view.findViewById(R.id.ivSelectedBadge)
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
        holder.tvStatus.setOnClickListener {
            if (!selectionMode) onStatusPillClick(item)
        }

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

        val isSelected = selectedIds.contains(app.applicationId)
        holder.ivSelectedBadge.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.ivSelectedBadge.alpha = if (isSelected) 1f else 0f
        holder.tvProfile.alpha = if (selectionMode && isSelected) 0.4f else 1f

        val context = holder.itemView.context
        if (isSelected) {
            holder.card.setStrokeColor(ContextCompat.getColor(context, R.color.color_accent))
            holder.card.strokeWidth = 4
        } else {
            holder.card.setStrokeColor(ContextCompat.getColor(context, R.color.color_divider))
            holder.card.strokeWidth = 2
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(app.applicationId, holder)
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

    private fun toggleSelection(applicationId: String, holder: VH) {
        if (selectedIds.contains(applicationId)) {
            selectedIds.remove(applicationId)
        } else {
            selectedIds.add(applicationId)
        }

        if (selectedIds.isEmpty()) {
            exitSelectionMode()
        } else {
            holder.ivSelectedBadge.animate().alpha(1f).setDuration(120).start()
            holder.tvProfile.animate().alpha(0.4f).setDuration(120).start()
            val context = holder.itemView.context
            val nowSelected = selectedIds.contains(applicationId)
            holder.card.setStrokeColor(
                ContextCompat.getColor(context, if (nowSelected) R.color.color_accent else R.color.color_divider)
            )
            holder.card.strokeWidth = if (nowSelected) 4 else 2
            if (!nowSelected) {
                holder.ivSelectedBadge.animate().alpha(0f).setDuration(120).start()
                holder.tvProfile.animate().alpha(1f).setDuration(120).start()
            }
        }
        onSelectionChanged?.invoke(selectedIds.size)
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

    fun getStudentCache(): Map<String, Triple<String, String, String>> = studentCache

    override fun getItemCount() = list.size
}