package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.example.jobmatrix.model.JobModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EmployerApplicationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerApplicationAdapter
    private val allData = mutableListOf<AppWithJob>()
    private val displayedData = mutableListOf<AppWithJob>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentFilter = "All"

    private lateinit var tabAll: TextView
    private lateinit var tabShortlisted: TextView
    private lateinit var tabRejected: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_applications)

        val filterJobId = intent.getStringExtra("jobId")

        recyclerView = findViewById(R.id.rvApplications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmployerApplicationAdapter(displayedData) { item ->
            val i = Intent(this, ApplicantDetailsActivity::class.java)
            i.putExtra("applicationId", item.app.applicationId)
            i.putExtra("studentId", item.app.studentId)
            i.putExtra("jobTitle", item.job?.title ?: item.app.jobTitle)
            i.putExtra("status", item.app.status)
            i.putExtra("resumeLink", item.app.resumeLink)
            i.putExtra("appliedAt", item.app.appliedAt)
            startActivity(i)
        }
        recyclerView.adapter = adapter

        tabAll = findViewById(R.id.tabAll)
        tabShortlisted = findViewById(R.id.tabShortlisted)
        tabRejected = findViewById(R.id.tabRejected)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        tabAll.setOnClickListener { setFilter("All") }
        tabShortlisted.setOnClickListener { setFilter("Shortlisted") }
        tabRejected.setOnClickListener { setFilter("Rejected") }

        loadData(filterJobId)
    }

    override fun onResume() {
        super.onResume()
        if (allData.isNotEmpty()) applyFilter()
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        val tabs = listOf(tabAll to "All", tabShortlisted to "Shortlisted", tabRejected to "Rejected")
        for ((tab, label) in tabs) {
            if (label == filter) {
                tab.setBackgroundResource(R.drawable.bg_chip_active)
                tab.setTextColor(android.graphics.Color.WHITE)
            } else {
                tab.setBackgroundResource(R.drawable.bg_chip)
                tab.setTextColor(resources.getColor(R.color.color_text_secondary, theme))
            }
        }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = allData.filter {
            currentFilter == "All" || it.app.status.equals(currentFilter, ignoreCase = true)
        }.sortedByDescending { it.app.appliedAt }
        displayedData.clear()
        displayedData.addAll(filtered)
        adapter.notifyDataSetChanged()
        updateTabCounts()
    }

    private fun updateTabCounts() {
        val all = allData.size
        val shortlisted = allData.count { it.app.status.equals("Shortlisted", true) }
        val rejected = allData.count { it.app.status.equals("Rejected", true) }
        tabAll.text = "All ($all)"
        tabShortlisted.text = "Shortlisted ($shortlisted)"
        tabRejected.text = "Rejected ($rejected)"
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData(filterJobId: String?) {
        val employerId = auth.currentUser?.uid ?: return

        db.collection("jobs").whereEqualTo("employerId", employerId).get()
            .addOnSuccessListener { jobDocs ->
                val jobMap = HashMap<String, JobModel>()
                for (doc in jobDocs) {
                    doc.toObject(JobModel::class.java).let { jobMap[it.jobId] = it }
                }

                val jobIds = if (filterJobId != null) listOf(filterJobId) else jobMap.keys.toList()
                if (jobIds.isEmpty()) { applyFilter(); return@addOnSuccessListener }

                db.collection("applications").whereIn("jobId", jobIds.take(30)).get()
                    .addOnSuccessListener { appDocs ->
                        allData.clear()
                        for (doc in appDocs) {
                            val app = doc.toObject(ApplicationModel::class.java).copy(applicationId = doc.id)
                            allData.add(AppWithJob(app, jobMap[app.jobId]))
                        }
                        applyFilter()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load applications", Toast.LENGTH_SHORT).show()
                    }
            }
    }
}