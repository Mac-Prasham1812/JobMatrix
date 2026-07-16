package com.example.jobmatrix.employer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.example.jobmatrix.profile.ProfileActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jobmatrix.app.R

class EmployerDashboardActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerJobAdapter
    private val jobList = mutableListOf<JobModel>()
    private val db = FirebaseFirestore.getInstance()
    private var jobListener: ListenerRegistration? = null

    private lateinit var ivProfile: ImageView
    private lateinit var tvEmployerName: TextView
    private lateinit var tvAppliedCount: TextView
    private lateinit var tvReviewCount: TextView
    private lateinit var tvShortlistedCount: TextView
    private lateinit var btnAddJob: Button
    private lateinit var tvViewAll: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_dashboard)

        initViews()
        setupRecycler()
        setupClicks()
        setEmployerName()
        loadEmployerJobs()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.rvEmployerJobs)
        ivProfile = findViewById(R.id.ivProfile)
        tvEmployerName = findViewById(R.id.tvEmployerName)
        tvAppliedCount = findViewById(R.id.tvAppliedCount)
        tvReviewCount = findViewById(R.id.tvReviewCount)
        tvShortlistedCount = findViewById(R.id.tvShortlistedCount)
        btnAddJob = findViewById(R.id.btnAddJob)
        tvViewAll = findViewById(R.id.tvViewAll)
    }

    private fun setupRecycler() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmployerJobAdapter(jobList)
        recyclerView.adapter = adapter
    }

    private fun setupClicks() {
        ivProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnAddJob.setOnClickListener {
            startActivity(Intent(this, AddJobActivity::class.java))
        }

        tvViewAll.setOnClickListener {
            if (jobList.isNotEmpty()) {
                recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private fun setEmployerName() {
        val user = FirebaseAuth.getInstance().currentUser
        tvEmployerName.text = user?.displayName?.takeIf { it.isNotBlank() } ?: "Employer"
    }

    private fun loadEmployerJobs() {
        val employerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        jobListener = db.collection("jobs")
            .whereEqualTo("employerId", employerId)
            .whereEqualTo("status", "Active")
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                jobList.clear()

                val sortedList = snapshots.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(JobModel::class.java)
                    } catch (_: Exception) {
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
                updateOverviewCounts(sortedList)
            }
    }

    private fun updateOverviewCounts(jobs: List<JobModel>) {
        var applied = 0
        var inReview = 0
        var shortlisted = 0

        for (job in jobs) {
            applied += getCount(job, "appliedCount", "applicationsCount", "totalApplicants")
            inReview += getCount(job, "reviewCount", "inReviewCount")
            shortlisted += getCount(job, "shortlistedCount")
        }

        tvAppliedCount.text = applied.toString().padStart(2, '0')
        tvReviewCount.text = inReview.toString().padStart(2, '0')
        tvShortlistedCount.text = shortlisted.toString().padStart(2, '0')
    }

    private fun getCount(job: JobModel, vararg fieldNames: String): Int {
        for (fieldName in fieldNames) {
            try {
                val field = job.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                val value = field.get(job)
                if (value is Int) return value
                if (value is Long) return value.toInt()
            } catch (_: Exception) {
            }
        }
        return 0
    }

    override fun onDestroy() {
        super.onDestroy()
        jobListener?.remove()
    }
}