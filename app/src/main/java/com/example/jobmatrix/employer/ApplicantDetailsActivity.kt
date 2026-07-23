package com.example.jobmatrix.employer

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.jobmatrix.network.NotifyRequest
import com.example.jobmatrix.network.RetrofitClient
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApplicantDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var applicationId: String
    private lateinit var studentId: String
    private var resumeLink: String = ""
    private var studentFcmToken: String = ""

    private lateinit var tvName: TextView
    private lateinit var tvJobTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAppliedAgo: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvExperience: TextView
    private lateinit var chipSkills: ChipGroup
    private lateinit var tvResumeName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_applicant_details)

        applicationId = intent.getStringExtra("applicationId") ?: ""
        studentId = intent.getStringExtra("studentId") ?: ""
        resumeLink = intent.getStringExtra("resumeLink") ?: ""
        val jobTitle = intent.getStringExtra("jobTitle") ?: ""
        val status = intent.getStringExtra("status") ?: "Applied"
        val appliedAt = intent.getLongExtra("appliedAt", 0L)

        tvName = findViewById(R.id.tvName)
        tvJobTitle = findViewById(R.id.tvJobTitle)
        tvStatus = findViewById(R.id.tvStatus)
        tvAppliedAgo = findViewById(R.id.tvAppliedAgo)
        tvPhone = findViewById(R.id.tvPhone)
        tvEmail = findViewById(R.id.tvEmail)
        tvExperience = findViewById(R.id.tvExperience)
        chipSkills = findViewById(R.id.chipSkills)
        tvResumeName = findViewById(R.id.tvResumeName)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        tvJobTitle.text = jobTitle
        tvAppliedAgo.text = DateUtils.getRelativeTimeSpanString(appliedAt)
        setStatusUi(status)

        findViewById<Button>(R.id.btnShortlist).setOnClickListener { updateStatus("Shortlisted") }
        findViewById<Button>(R.id.btnReject).setOnClickListener { updateStatus("Rejected") }
        findViewById<Button>(R.id.btnMessage).setOnClickListener {
            val intent = Intent(this, com.example.jobmatrix.chat.ChatActivity::class.java)
            intent.putExtra("applicationId", applicationId)
            startActivity(intent)
        }
        findViewById<android.view.View>(R.id.rowResume).setOnClickListener { openResume() }

        loadStudent()
    }

    private fun setStatusUi(status: String) {
        tvStatus.text = status.replaceFirstChar { it.uppercase() }
        when (status.lowercase()) {
            "shortlisted" -> tvStatus.setBackgroundResource(R.drawable.bg_status_shortlisted)
            "rejected" -> tvStatus.setBackgroundResource(R.drawable.bg_status_rejected)
            else -> tvStatus.setBackgroundResource(R.drawable.bg_status_applied)
        }
    }

    private fun loadStudent() {
        if (studentId.isEmpty()) return
        db.collection("users").document(studentId).get()
            .addOnSuccessListener { doc ->
                tvName.text = doc.getString("name") ?: "Unknown"
                tvPhone.text = doc.getString("phone")?.ifBlank { "Not available" } ?: "Not available"
                tvEmail.text = doc.getString("email") ?: "Not available"
                tvExperience.text = doc.getString("experience")?.ifBlank { "N/A" } ?: "N/A"
                tvResumeName.text = if (resumeLink.isNotBlank()) resumeLink.substringAfterLast("/") else "No resume uploaded"
                studentFcmToken = doc.getString("fcmToken") ?: ""

                val skills = doc.get("skills") as? List<*> ?: emptyList<String>()
                chipSkills.removeAllViews()
                for (s in skills) {
                    val chip = Chip(this).apply {
                        text = s.toString()
                        isClickable = false
                        isCheckable = false
                    }
                    chipSkills.addView(chip)
                }
            }
    }

    private fun updateStatus(newStatus: String) {
        val jobTitle = intent.getStringExtra("jobTitle") ?: ""
        val companyName = intent.getStringExtra("companyName") ?: ""

        val message = if (newStatus == "Shortlisted")
            "You have been shortlisted for $jobTitle at $companyName."
        else
            "Your application for $jobTitle at $companyName was not selected."

        db.collection("applications").document(applicationId)
            .update(mapOf("status" to newStatus))
            .addOnSuccessListener {
                setStatusUi(newStatus)
                Toast.makeText(this, "Status updated & student notified", Toast.LENGTH_SHORT).show()
                addNotification(newStatus, message)

                db.collection("users").document(studentId).get()
                    .addOnSuccessListener { doc ->
                        val token = doc.getString("fcmToken") ?: ""
                        if (token.isNotBlank()) sendPush(token, "JobMatrix", message)
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addNotification(type: String, message: String) {
        val jobTitle = intent.getStringExtra("jobTitle") ?: ""
        val companyName = intent.getStringExtra("companyName") ?: ""
        val notif = hashMapOf(
            "studentId" to studentId,
            "applicationId" to applicationId,
            "jobTitle" to jobTitle,
            "companyName" to companyName,
            "message" to message,
            "type" to type,
            "createdAt" to System.currentTimeMillis(),
            "isRead" to false
        )
        db.collection("notifications").add(notif)
    }

//    private fun showMessageDialog() {
//        val et = EditText(this)
//        et.hint = "Type a message to the student..."
//        AlertDialog.Builder(this)
//            .setTitle("Send Message")
//            .setView(et)
//            .setPositiveButton("Send") { _, _ ->
//                val text = et.text.toString().trim()
//                if (text.isEmpty()) return@setPositiveButton
//                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
//                addNotification("Message", text)
//
//                db.collection("users").document(studentId).get()
//                    .addOnSuccessListener { doc ->
//                        val token = doc.getString("fcmToken") ?: ""
//                        if (token.isNotBlank()) sendPush(token, "JobMatrix", text)
//                    }
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }

    private fun sendPush(token: String, title: String, body: String) {
        if (token.isBlank()) return
        lifecycleScope.launch {
            try {
                RetrofitClient.api.sendNotification(NotifyRequest(token, title, body))
            } catch (e: Exception) {
                Toast.makeText(this@ApplicantDetailsActivity, "Push failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openResume() {
        if (resumeLink.isBlank()) {
            Toast.makeText(this, "Resume not available", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val token = "Bearer ${getIdToken()}"
                val response = RetrofitClient.api.getResumeUrl(token, resumeLink)
                if (response.isSuccessful) {
                    val url = response.body()?.url
                    if (!url.isNullOrBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) })
                    } else {
                        Toast.makeText(this@ApplicantDetailsActivity, "Resume not available", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ApplicantDetailsActivity, "Failed to load resume", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ApplicantDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getIdToken(): String = suspendCancellableCoroutine { cont ->
        val user = auth.currentUser
        if (user == null) { cont.resumeWithException(Exception("Not logged in")); return@suspendCancellableCoroutine }
        user.getIdToken(true)
            .addOnSuccessListener { result -> cont.resume(result.token ?: "") }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}