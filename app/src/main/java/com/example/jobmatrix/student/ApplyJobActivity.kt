package com.example.jobmatrix.student

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class ApplyJobActivity : AppCompatActivity() {

    private lateinit var etResumeLink: EditText
    private lateinit var btnSubmit: Button

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var jobId: String
    private lateinit var jobTitle: String
    private lateinit var companyName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply_job)

        // Receive data from JobAdapter
        jobId = intent.getStringExtra("jobId") ?: ""
        jobTitle = intent.getStringExtra("jobTitle") ?: ""
        companyName = intent.getStringExtra("companyName") ?: ""

        etResumeLink = findViewById(R.id.etResumeLink)
        btnSubmit = findViewById(R.id.btnSubmitApplication)

        btnSubmit.setOnClickListener {
            submitApplication()
        }
    }

    private fun submitApplication() {
        val resumeLink = etResumeLink.text.toString().trim()

        if (resumeLink.isEmpty()) {
            Toast.makeText(this, "Please paste resume link", Toast.LENGTH_SHORT).show()
            return
        }

        if (!resumeLink.contains("drive.google.com")) {
            Toast.makeText(this, "Please enter a valid Google Drive link", Toast.LENGTH_SHORT).show()
            return
        }

        val studentId = auth.currentUser?.uid
        if (studentId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (jobId.isEmpty()) {
            Toast.makeText(this, "Invalid job", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate application ID
        val applicationId = db.collection("applications").document().id

        val applicationData = hashMapOf(
            "applicationId" to applicationId,
            "jobId" to jobId,
            "jobTitle" to jobTitle,
            "companyName" to companyName,
            "studentId" to studentId,
            "resumeLink" to resumeLink,
            "status" to "Applied",
            "hasNotification" to false,
            "isRead" to false,
            "appliedAt" to System.currentTimeMillis()
        )

        db.collection("applications")
            .document(applicationId)
            .set(applicationData)
            .addOnSuccessListener {
                Toast.makeText(this, "Application submitted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to submit application", Toast.LENGTH_SHORT).show()
            }
    }
}
