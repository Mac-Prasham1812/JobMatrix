package com.example.jobmatrix.employer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EmployerApplicationAdapter(
    private val list: List<ApplicationModel>,
    private val onResumeClick: (String) -> Unit
) : RecyclerView.Adapter<EmployerApplicationAdapter.VH>() {

    private val db = FirebaseFirestore.getInstance()

    // Cache student details
    private val studentCache = HashMap<String, Triple<String, String, String>>()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvStudentName: TextView = view.findViewById(R.id.tvStudentName)
        val tvStudentEmail: TextView = view.findViewById(R.id.tvStudentEmail)
        val tvStudentPhone: TextView = view.findViewById(R.id.tvStudentPhone)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnView: Button = view.findViewById(R.id.btnViewResume)
        val tvProfile: TextView = view.findViewById(R.id.tvProfile)
        val btnShortlist: Button = view.findViewById(R.id.btnShortlist)
        val btnReject: Button = view.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_application, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = list[position]

        // Status text
        holder.tvStatus.text = "Status: ${app.status}"

        // Status color
        when (app.status.lowercase()) {
            "applied" -> holder.tvStatus.setTextColor(
                holder.itemView.context.getColor(R.color.blue)
            )
            "shortlisted" -> holder.tvStatus.setTextColor(
                holder.itemView.context.getColor(R.color.green)
            )
            "rejected" -> holder.tvStatus.setTextColor(
                holder.itemView.context.getColor(R.color.red)
            )
            else -> holder.tvStatus.setTextColor(
                holder.itemView.context.getColor(android.R.color.darker_gray)
            )
        }

        // Student data (cache first)
        if (studentCache.containsKey(app.studentId)) {
            val (name, email, phone) = studentCache[app.studentId]!!
            setStudentData(holder, name, email, phone)
        } else {
            db.collection("users")
                .document(app.studentId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("name") ?: "Unknown"
                        val email = doc.getString("email") ?: "Not available"
                        val phone = doc.getString("phone") ?: "Not available"

                        studentCache[app.studentId] = Triple(name, email, phone)
                        setStudentData(holder, name, email, phone)
                    } else {
                        holder.tvStudentName.text = "User not found"
                        holder.tvStudentEmail.text = ""
                        holder.tvStudentPhone.text = ""
                        holder.tvProfile.text = "?"
                    }
                }
                .addOnFailureListener {
                    holder.tvStudentName.text = "Failed to load student"
                    holder.tvProfile.text = "?"
                }
        }

        // View Resume
        holder.btnView.setOnClickListener {
            if (app.resumeLink.isNotEmpty()) {
                onResumeClick(app.resumeLink)
            }
        }

        // SHORTLIST
        holder.btnShortlist.setOnClickListener {
            updateStatus(app, "Shortlisted", holder)
        }

        // REJECT
        holder.btnReject.setOnClickListener {
            updateStatus(app, "Rejected", holder)
        }
    }

    private fun setStudentData(
        holder: VH,
        name: String,
        email: String,
        phone: String
    ) {
        holder.tvStudentName.text = name
        holder.tvStudentEmail.text = "Email: $email"
        holder.tvStudentPhone.text = "Phone: $phone"

        // Profile circle letter
        holder.tvProfile.text =
            if (name.isNotEmpty()) name.first().uppercaseChar().toString() else "?"
    }

    // Update status + create notification trigger
    private fun updateStatus(app: ApplicationModel, newStatus: String, holder: VH) {

        val message = when (newStatus) {
            "Shortlisted" -> "🎉 You have been shortlisted for further rounds."
            "Rejected" -> "❌ Your application was not selected."
            else -> ""
        }

        db.collection("applications")
            .document(app.applicationId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "hasNotification" to true,
                    "notificationMessage" to message,
                    "isRead" to false
                )
            )

            .addOnSuccessListener {
                Toast.makeText(
                    holder.itemView.context,
                    "Status updated & student notified",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to update status",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    override fun getItemCount() = list.size
}
