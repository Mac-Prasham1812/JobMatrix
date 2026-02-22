package com.example.jobmatrix.employer

import com.example.jobmatrix.profile.ProfileActivity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.jobmatrix.app.R

class EmployerDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerJobAdapter
    private val jobList = mutableListOf<JobModel>()
    private val db = FirebaseFirestore.getInstance()
    private var jobListener: ListenerRegistration? = null
    private lateinit var ivProfile: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_dashboard)

        recyclerView = findViewById(R.id.rvEmployerJobs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmployerJobAdapter(jobList)
        recyclerView.adapter = adapter
        ivProfile = findViewById(R.id.ivProfile)

        findViewById<Button>(R.id.btnAddJob).setOnClickListener {
            startActivity(Intent(this, AddJobActivity::class.java))
        }

        loadEmployerJobs()

        ivProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }


    }

    private fun loadEmployerJobs() {
        val employerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        jobListener = db.collection("jobs")
            .whereEqualTo("employerId", employerId)
            .whereEqualTo("status", "Active")
            .addSnapshotListener { snapshots, error ->

                if (error != null || snapshots == null) return@addSnapshotListener

                jobList.clear()

                val sortedList = snapshots.mapNotNull { doc ->
                    try {
                        doc.toObject(JobModel::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { job ->
                    when (val time = job.createdAt) {
                        is Timestamp -> time.toDate().time
                        is Long -> time
                        else -> 0L
                    }
                }

                jobList.addAll(sortedList)
                adapter.notifyDataSetChanged()
            }
    }




    override fun onDestroy() {
        super.onDestroy()
        jobListener?.remove()
    }
}
