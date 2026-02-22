package com.example.jobmatrix.admin

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EditJobActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etCompany: EditText
    private lateinit var etLocation: EditText
    private lateinit var etCategory: EditText
    private lateinit var etSalary: EditText
    private lateinit var etExperience: EditText
    private lateinit var etCompanyOverview: EditText
    private lateinit var btnUpdateJob: Button

    private val db = FirebaseFirestore.getInstance()
    private var jobId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_job)

        // Bind views
        etTitle = findViewById(R.id.etTitle)
        etCompany = findViewById(R.id.etCompany)
        etLocation = findViewById(R.id.etLocation)
        etCategory = findViewById(R.id.etCategory)
        etSalary = findViewById(R.id.etSalary)
        etExperience = findViewById(R.id.etExperience)
        etCompanyOverview = findViewById(R.id.etCompanyOverview)
        btnUpdateJob = findViewById(R.id.btnUpdateJob)

        // Get jobId from intent
        jobId = intent.getStringExtra("jobId")

        if (jobId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid job selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadJobDetails()

        btnUpdateJob.setOnClickListener {
            animateButton()
            updateJobDetails()
        }
    }

    private fun loadJobDetails() {
        db.collection("jobs")
            .document(jobId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etTitle.setText(doc.getString("title") ?: "")
                    etCompany.setText(doc.getString("company") ?: "")
                    etLocation.setText(doc.getString("location") ?: "")
                    etCategory.setText(doc.getString("category") ?: "")
                    etSalary.setText(doc.getString("salary") ?: "")
                    etExperience.setText(doc.getString("experience") ?: "")
                    etCompanyOverview.setText(doc.getString("companyOverview") ?: "")
                } else {
                    Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load job details", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateJobDetails() {
        val title = etTitle.text.toString().trim()
        val company = etCompany.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val category = etCategory.text.toString().trim()
        val salary = etSalary.text.toString().trim()
        val experience = etExperience.text.toString().trim()
        val overview = etCompanyOverview.text.toString().trim()

        if (title.isEmpty() || company.isEmpty() || location.isEmpty() || category.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val updateMap = hashMapOf<String, Any>(
            "title" to title,
            "company" to company,
            "location" to location,
            "category" to category,
            "salary" to salary,
            "experience" to experience,
            "companyOverview" to overview
        )

        btnUpdateJob.isEnabled = false
        btnUpdateJob.text = "Updating..."

        db.collection("jobs")
            .document(jobId!!)
            .update(updateMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Job updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                btnUpdateJob.isEnabled = true
                btnUpdateJob.text = "UPDATE JOB"
                Toast.makeText(this, "Update failed. Try again.", Toast.LENGTH_SHORT).show()
            }
    }

    // Premium button press animation
    private fun animateButton() {
        btnUpdateJob.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(80)
            .withEndAction {
                btnUpdateJob.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .start()
            }.start()
    }
}
