package com.example.jobmatrix.student

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class JobDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvTitle: TextView
    private lateinit var tvCompanyOverview: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvExperience: TextView
    private lateinit var tvSalary: TextView
    private lateinit var btnApply: Button
    private lateinit var tvCompany: TextView
    private lateinit var tvLocation: TextView

    private var jobId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_details)

        tvTitle = findViewById(R.id.tvTitle)
        tvCompany = findViewById(R.id.tvCompany)
        tvLocation = findViewById(R.id.tvLocation)
        tvCompanyOverview = findViewById(R.id.tvCompanyOverview)
        tvCategory = findViewById(R.id.tvCategory)
        tvExperience = findViewById(R.id.tvExperience)
        tvSalary = findViewById(R.id.tvSalary)
        btnApply = findViewById(R.id.btnApply)

        jobId = intent.getStringExtra("jobId")

        if (jobId.isNullOrEmpty()) {
            Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadJobDetails(jobId!!)

        // Redirect to ApplyJobActivity
        btnApply.setOnClickListener {
            val intent = Intent(this, ApplyJobActivity::class.java)
            intent.putExtra("jobId", jobId)
            startActivity(intent)
        }
    }

    private fun loadJobDetails(jobId: String) {
        db.collection("jobs")
            .document(jobId)
            .get()
            .addOnSuccessListener { doc ->
                val job = doc.toObject(JobModel::class.java)
                if (job != null) {
                    bindJob(job)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load job", Toast.LENGTH_SHORT).show()
            }
    }

    private fun bindJob(job: JobModel) {
        tvTitle.text = job.title
        tvCompany.text = "Company: ${job.company}"
        tvLocation.text = "Location: ${job.location}"

        tvCompanyOverview.text = if (job.companyOverview.isNotEmpty()) {
            job.companyOverview
        } else {
            "Company information not provided."
        }

        tvCategory.text = "Category: ${job.category}"
        tvExperience.text = "Experience Required: ${job.experience}"
        tvSalary.text = "₹${job.salary}"
    }
}
