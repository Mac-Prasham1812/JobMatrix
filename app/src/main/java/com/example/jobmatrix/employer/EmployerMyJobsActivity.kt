package com.example.jobmatrix.employer

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.example.jobmatrix.profile.ProfileActivity
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

    private lateinit var rvShimmer: RecyclerView
    private var shimmerStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_my_jobs)

        recyclerView = findViewById(R.id.rvMyJobs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmployerJobAdapter(displayedJobs)
        recyclerView.adapter = adapter

        rvShimmer = findViewById(R.id.rvShimmerMyJobs)
        rvShimmer.layoutManager = LinearLayoutManager(this)
        rvShimmer.adapter = com.example.jobmatrix.student.ShimmerAdapter()

        etSearch = findViewById(R.id.etSearch)
        tabAll = findViewById(R.id.tabAll)
        tabActive = findViewById(R.id.tabActive)
        tabInactive = findViewById(R.id.tabInactive)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        findViewById<CardView>(R.id.fabAddJob).setOnClickListener {
            startActivity(Intent(this, AddJobActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            startActivity(Intent(this, EmployerDashboardActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.navApplications).setOnClickListener {
            // startActivity(Intent(this, EmployerApplicationsActivity::class.java))
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


    private fun setActiveNav(activeId: Int) {
        val navItems = listOf(R.id.navDashboard, R.id.navMyJobs, R.id.navApplications, R.id.navProfile)
        for (id in navItems) {
            findViewById<LinearLayout>(id).isSelected = (id == activeId)
        }
    }

    private fun loadJobs() {
        val employerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        showShimmer()
        db.collection("jobs")
            .whereEqualTo("employerId", employerId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                allJobs.clear()
                for (doc in snapshots.documents) {
                    doc.toObject(JobModel::class.java)?.let { allJobs.add(it) }
                }
                applyFilters()
                hideShimmer()
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

        val sorted = if (currentFilter == "Inactive") {
            filtered.sortedByDescending { it.deactivatedAt }
        } else if (currentFilter == "All") {
            val active = filtered.filter { it.status == "Active" }
            val inactive = filtered.filter { it.status != "Active" }.sortedByDescending { it.deactivatedAt }
            active + inactive
        } else {
            filtered
        }

        displayedJobs.clear()
        displayedJobs.addAll(sorted)
        adapter.notifyDataSetChanged()
        recyclerView.scheduleLayoutAnimation()
        recyclerView.alpha = 0f
        recyclerView.animate().alpha(1f).setDuration(250).start()
    }



    private fun showShimmer() {
        shimmerStartTime = System.currentTimeMillis()
        rvShimmer.visibility = android.view.View.VISIBLE
        recyclerView.visibility = android.view.View.GONE
    }

    private fun hideShimmer() {
        val elapsed = System.currentTimeMillis() - shimmerStartTime
        val delay = (600 - elapsed).coerceAtLeast(0)
        rvShimmer.postDelayed({
            rvShimmer.visibility = android.view.View.GONE
            recyclerView.visibility = android.view.View.VISIBLE
        }, delay)
    }

    override fun onResume() {
        super.onResume()
        setActiveNav(R.id.navMyJobs)
    }
}