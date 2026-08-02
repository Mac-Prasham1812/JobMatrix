package com.example.jobmatrix.employer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.auth.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmployerProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.jobmatrix.app.R.layout.activity_employer_profile)

        findViewById<ImageView>(com.jobmatrix.app.R.id.btnClose).setOnClickListener { finish() }

        loadEmployerData()
        loadPipelineCounts()

        findViewById<LinearLayout>(com.jobmatrix.app.R.id.rowMyJobs).setOnClickListener {
            startActivity(Intent(this, EmployerMyJobsActivity::class.java))
        }

        findViewById<LinearLayout>(com.jobmatrix.app.R.id.rowMyApplications).setOnClickListener {
            startActivity(Intent(this, EmployerApplicationsActivity::class.java))
        }

        findViewById<LinearLayout>(com.jobmatrix.app.R.id.rowCompanyProfile).setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(com.jobmatrix.app.R.id.rowNotifications).setOnClickListener {
            startActivity(Intent(this, EmployerNotificationActivity::class.java))
        }

        findViewById<LinearLayout>(com.jobmatrix.app.R.id.rowTheme).setOnClickListener {
            val currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
            val newMode =
                if (currentMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putInt("night_mode", newMode).apply()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(newMode)
        }

        findViewById<LinearLayout>(com.jobmatrix.app.R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadEmployerData() {
        val user = auth.currentUser ?: return
        findViewById<TextView>(com.jobmatrix.app.R.id.tvEmail).text = user.email ?: "No Email"

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: user.displayName ?: "Employer"
                findViewById<TextView>(com.jobmatrix.app.R.id.tvName).text = name
                findViewById<TextView>(com.jobmatrix.app.R.id.tvAvatarInitials).text =
                    name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2)
                        .joinToString("")
            }
    }

    private fun loadPipelineCounts() {
        val employerId = auth.currentUser?.uid ?: return
        db.collection("jobs").whereEqualTo("employerId", employerId).get()
            .addOnSuccessListener { jobDocs ->
                val jobIds = jobDocs.documents.map { it.id }
                if (jobIds.isEmpty()) return@addOnSuccessListener
                db.collection("applications").whereIn("jobId", jobIds.take(30)).get()
                    .addOnSuccessListener { docs ->
                        val applied = docs.count { it.getString("status") == "Applied" }
                        val review = docs.count { it.getString("status") == "In Review" }
                        val shortlisted = docs.count { it.getString("status") == "Shortlisted" }
                        findViewById<TextView>(com.jobmatrix.app.R.id.tvAppliedCount).text =
                            applied.toString()
                        findViewById<TextView>(com.jobmatrix.app.R.id.tvInReviewCount).text =
                            review.toString()
                        findViewById<TextView>(com.jobmatrix.app.R.id.tvShortlistedCount).text =
                            shortlisted.toString()
                    }
            }
    }
}