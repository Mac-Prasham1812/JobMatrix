package com.example.jobmatrix.employer

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EmployerMyJobsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerJobAdapter
    private val allJobs = mutableListOf<JobModel>()
    private val displayedJobs = mutableListOf<JobModel>()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var etSearch: EditText
    private lateinit var tabAll: TextView
    private lateinit var tabActive: TextView
    private lateinit var tabInactive: TextView
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_my_jobs)

        recyclerView = findViewById(R.id.rvMyJobs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmployerJobAdapter(displayedJobs)
        recyclerView.adapter = adapter

        etSearch = findViewById(R.id.etSearch)
        tabAll = findViewById(R.id.tabAll)
        tabActive = findViewById(R.id.tabActive)
        tabInactive = findViewById(R.id.tabInactive)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<CardView>(R.id.fabAddJob).setOnClickListener {
            startActivity(Intent(this, AddJobActivity::class.java))
        }

        tabAll.setOnClickListener { setFilter("All") }
        tabActive.setOnClickListener { setFilter("Active") }
        tabInactive.setOnClickListener { setFilter("Inactive") }

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        loadJobs()
    }

    private fun loadJobs() {
        val employerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("jobs")
            .whereEqualTo("employerId", employerId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                allJobs.clear()
                for (doc in snapshots.documents) {
                    doc.toObject(JobModel::class.java)?.let { allJobs.add(it) }
                }
                applyFilters()
            }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter

        val tabs = listOf(tabAll to "All", tabActive to "Active", tabInactive to "Inactive")
        for ((tab, label) in tabs) {
            if (label == filter) {
                tab.setBackgroundResource(R.drawable.bg_chip_active)
                tab.setTextColor(android.graphics.Color.WHITE)
            } else {
                tab.setBackgroundResource(R.drawable.bg_chip)
                tab.setTextColor(resources.getColor(R.color.color_text_secondary, theme))
            }
        }

        applyFilters()
    }

    private fun applyFilters() {
        val query = etSearch.text.toString().trim().lowercase()
        val filtered = allJobs.filter { job ->
            val matchesTab = when (currentFilter) {
                "Active" -> job.status.equals("Active", ignoreCase = true)
                "Inactive" -> job.status.equals("Inactive", ignoreCase = true)
                else -> true
            }
            val matchesSearch = query.isEmpty() || job.title.lowercase().contains(query) || job.category.lowercase().contains(query)
            matchesTab && matchesSearch
        }
        displayedJobs.clear()
        displayedJobs.addAll(filtered)
        adapter.notifyDataSetChanged()
    }
}