package com.example.jobmatrix.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import com.example.jobmatrix.auth.LoginActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnClose: ImageView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvRole = findViewById(R.id.tvRole)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        btnLogout = findViewById(R.id.btnLogout)
        btnClose = findViewById(R.id.btnClose)

        loadUserData()
        loadPipelineCounts()

        btnClose.setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.rowSkills).setOnClickListener {
            startActivity(Intent(this, SkillsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowApplications).setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.rowResume).setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.rowNotifications).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.student.NotificationActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        tvEmail.text = user.email ?: "No Email"

        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: "Unknown"
                    tvName.text = name
                    tvRole.text = doc.getString("role") ?: "User"
                    tvAvatarInitials.text = name.trim().split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .take(2)
                        .joinToString("")
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPipelineCounts() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("applications").whereEqualTo("studentId", uid)
            .get()
            .addOnSuccessListener { docs ->
                val applied = docs.count { it.getString("status") == "Applied" }
                val shortlisted = docs.count { it.getString("status") == "Shortlisted" }
                val rejected = docs.count { it.getString("status") == "Rejected" }

                findViewById<TextView>(R.id.tvAppliedCount).text = applied.toString()
                findViewById<TextView>(R.id.tvInReviewCount).text = rejected.toString()
                findViewById<TextView>(R.id.tvShortlistedCount).text = shortlisted.toString()
            }
    }
}