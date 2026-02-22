package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.os.Bundle
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
    private lateinit var btnUpdate: Button

    private val db = FirebaseFirestore.getInstance()
    private lateinit var jobId: String

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_job)

        jobId = intent.getStringExtra("jobId") ?: run {
            Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Bind views
        etTitle = findViewById(R.id.etTitle)
        etCompany = findViewById(R.id.etCompany)
        etLocation = findViewById(R.id.etLocation)
        etCategory = findViewById(R.id.etCategory)
        etSalary = findViewById(R.id.etSalary)
        etExperience = findViewById(R.id.etExperience)
        etCompanyOverview = findViewById(R.id.etCompanyOverview)
        btnUpdate = findViewById(R.id.btnUpdateJob)

        loadJobDetails()

        btnUpdate.setOnClickListener {
            updateJob()
        }
    }

    // Load existing job data into fields
    private fun loadJobDetails() {
        db.collection("jobs")
            .document(jobId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etTitle.setText(doc.getString("title"))
                    etCompany.setText(doc.getString("company"))
                    etLocation.setText(doc.getString("location"))
                    etCategory.setText(doc.getString("category"))
                    etSalary.setText(doc.getString("salary"))
                    etExperience.setText(doc.getString("experience"))
                    etCompanyOverview.setText(doc.getString("companyOverview"))
                } else {
                    Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load job details", Toast.LENGTH_SHORT).show()
            }
    }

    // Update job in Firestore
    private fun updateJob() {

        val title = etTitle.text.toString().trim()
        val company = etCompany.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val category = etCategory.text.toString().trim()
        val salary = etSalary.text.toString().trim()
        val experience = etExperience.text.toString().trim()
        val overview = etCompanyOverview.text.toString().trim()

        if (title.isEmpty() || company.isEmpty() || location.isEmpty()
            || category.isEmpty() || salary.isEmpty() || experience.isEmpty()
        ) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
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

        db.collection("jobs")
            .document(jobId)
            .update(updateMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Job updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update job", Toast.LENGTH_SHORT).show()
            }
    }
}
