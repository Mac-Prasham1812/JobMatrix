package com.example.jobmatrix.employer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AddJobActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_job)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etCompany = findViewById<EditText>(R.id.etCompany)
        val etLocation = findViewById<EditText>(R.id.etLocation)
        val etCategory = findViewById<EditText>(R.id.etCategory)
        val etSalary = findViewById<EditText>(R.id.etSalary)
        val etExperience = findViewById<EditText>(R.id.etExperience)
        val etCompanyOverview = findViewById<EditText>(R.id.etCompanyOverview)

        val btnPostJob = findViewById<Button>(R.id.btnPostJob)

        btnPostJob.setOnClickListener {

            val title = etTitle.text.toString().trim()
            val company = etCompany.text.toString().trim()
            val location = etLocation.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val salary = etSalary.text.toString().trim()
            val experience = etExperience.text.toString().trim()
            val companyOverview = etCompanyOverview.text.toString().trim()

            if (title.isEmpty() || company.isEmpty() || location.isEmpty()
                || category.isEmpty() || salary.isEmpty() || experience.isEmpty()
                || companyOverview.isEmpty()
            ) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val employerId = auth.currentUser?.uid
            if (employerId == null) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val jobId = db.collection("jobs").document().id

            val jobMap = hashMapOf(
                "jobId" to jobId,
                "title" to title,
                "company" to company,
                "location" to location,
                "category" to category,
                "salary" to salary,
                "experience" to experience,
                "employerId" to employerId,
                "companyOverview" to companyOverview,
                "status" to "Active",
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("jobs")
                .document(jobId)
                .set(jobMap)
                .addOnSuccessListener {
                    Toast.makeText(this, "Job Posted Successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
                }
        }
    }
}
