package com.example.jobmatrix.employer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EmployerApplicationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerApplicationAdapter
    private val list = mutableListOf<ApplicationModel>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_applications)

        val jobId = intent.getStringExtra("jobId")
        if (jobId.isNullOrEmpty()) {
            Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.rvApplications)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EmployerApplicationAdapter(list) { resumeLink ->
            openResume(resumeLink)
        }

        recyclerView.adapter = adapter

        loadApplications(jobId)
    }

    private fun loadApplications(jobId: String) {
        db.collection("applications")
            .whereEqualTo("jobId", jobId)
            .get()
            .addOnSuccessListener { documents ->
                list.clear()
                for (doc in documents) {
                    list.add(doc.toObject(ApplicationModel::class.java))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load applications", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openResume(resumeLink: String) {
        if (resumeLink.isBlank()) {
            Toast.makeText(this, "Resume link not available", Toast.LENGTH_SHORT).show()
            return
        }

        var url = resumeLink.trim()


        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open resume", Toast.LENGTH_SHORT).show()
        }
    }
}
