package com.example.jobmatrix.admin

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminApplicationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private var jobId: String? = null

    private val db = FirebaseFirestore.getInstance()
    private val appList = ArrayList<ApplicationAdminModel>()
    private lateinit var adapter: AdminApplicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_applications)

        recyclerView = findViewById(R.id.recyclerApplications)
        progressBar = findViewById(R.id.progressApplications)
        tvEmpty = findViewById(R.id.tvEmptyApplications)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminApplicationAdapter(appList)
        recyclerView.adapter = adapter

        // Get jobId FIRST
        jobId = intent.getStringExtra("jobId")

        if (jobId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid job selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Now load only this job's applications
        loadApplications()
    }

    private fun loadApplications() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        recyclerView.visibility = View.GONE

        db.collection("applications")
            .whereEqualTo("jobId", jobId)
            .get()
            .addOnSuccessListener { snapshot ->
                appList.clear()

                for (doc in snapshot.documents) {
                    val app = ApplicationAdminModel(
                        applicationId = doc.id,
                        studentId = doc.getString("studentId") ?: "",
                        jobId = doc.getString("jobId") ?: "",
                        jobTitle = doc.getString("jobTitle") ?: "N/A",
                        companyName = doc.getString("companyName") ?: "N/A",
                        resumeLink = doc.getString("resumeLink") ?: "",
                        status = doc.getString("status") ?: "Applied"
                    )
                    appList.add(app)
                }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                if (appList.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load applications", Toast.LENGTH_SHORT).show()
            }
    }
}
