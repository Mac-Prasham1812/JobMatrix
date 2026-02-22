package com.example.jobmatrix.admin

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminJobsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private val db = FirebaseFirestore.getInstance()
    private val jobList = ArrayList<JobAdminModel>()
    private lateinit var adapter: AdminJobAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_jobs)

        recyclerView = findViewById(R.id.recyclerJobs)
        progressBar = findViewById(R.id.progressJobs)
        tvEmpty = findViewById(R.id.tvEmptyJobs)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminJobAdapter(jobList, this)
        recyclerView.adapter = adapter

        // Recycler entry animation
        recyclerView.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.recycler_fade_slide)

        loadJobs()
    }

    private fun loadJobs() {
        progressBar.visibility = View.VISIBLE

        db.collection("jobs")
            .get()
            .addOnSuccessListener { snapshot ->
                jobList.clear()

                for (doc in snapshot.documents) {
                    val job = JobAdminModel(
                        jobId = doc.id,
                        title = doc.getString("title") ?: "N/A",
                        company = doc.getString("company") ?: "N/A",
                        category = doc.getString("category") ?: "N/A",
                        status = doc.getString("status") ?: "Active"
                    )
                    jobList.add(job)
                }

                adapter.notifyDataSetChanged()
                recyclerView.scheduleLayoutAnimation()

                progressBar.visibility = View.GONE
                tvEmpty.visibility = if (jobList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load jobs", Toast.LENGTH_SHORT).show()
            }
    }
}
