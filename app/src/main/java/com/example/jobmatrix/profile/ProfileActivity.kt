package com.example.jobmatrix.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import com.example.jobmatrix.auth.LoginActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnClose: ImageView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val experienceList = listOf("Fresher", "1-2 Years", "3-5 Years", "5+ Years", "Other")

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

        findViewById<LinearLayout>(R.id.rowExperience).setOnClickListener { openExperienceSheet() }

        findViewById<LinearLayout>(R.id.rowSkills).setOnClickListener {
            startActivity(Intent(this, SkillsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowApplications).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.student.MyApplicationsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowSavedJobs).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.student.SavedJobsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowResume).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.settings.SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowNotifications).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    com.example.jobmatrix.student.NotificationActivity::class.java
                )
            )
        }
        btnLogout.setOnClickListener {
            com.example.jobmatrix.presence.PresenceManager.goOffline()
            auth.signOut()
            GoogleSignIn.getClient(this, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
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

    private fun openExperienceSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_selector, null)
        view.findViewById<TextView>(R.id.tvSheetTitle).text = "Select Experience"
        val listOptions = view.findViewById<android.widget.ListView>(R.id.listOptions)
        val adapter = android.widget.ArrayAdapter(
            this,
            R.layout.item_selector_option,
            R.id.tvOption,
            experienceList
        )
        listOptions.adapter = adapter
        listOptions.setOnItemClickListener { _, _, position, _ ->
            val selected = experienceList[position]
            if (selected == "Other") {
                dialog.dismiss()
                showCustomExperienceInput()
            } else {
                saveExperience(selected)
                dialog.dismiss()
            }
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showCustomExperienceInput() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_experience_input, null)
        val etYears = view.findViewById<android.widget.EditText>(R.id.etYears)

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val years = etYears.text.toString().trim()
            if (years.isNotBlank()) {
                saveExperience("$years Years")
                dialog.dismiss()
            }
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun saveExperience(value: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("experience", value)
            .addOnSuccessListener {
                Toast.makeText(this, "Experience updated", Toast.LENGTH_SHORT).show()
            }
    }
}