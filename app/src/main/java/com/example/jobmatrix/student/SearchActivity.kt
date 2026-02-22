package com.example.jobmatrix.student

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var jobAdapter: JobAdapter
    private lateinit var tvNoResult: TextView

    private lateinit var rvShimmer: RecyclerView
    private lateinit var shimmerAdapter: ShimmerAdapter

    private val allJobsList = mutableListOf<JobModel>()
    private val jobList = mutableListOf<JobModel>()

    // Chips
    private lateinit var chipAll: TextView
    private lateinit var chipDev: TextView
    private lateinit var chipDesign: TextView
    private lateinit var chipMarketing: TextView

    private var currentCategory = "All"
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etSearch = findViewById(R.id.etSearch)
        tvNoResult = findViewById(R.id.tvNoResult)
        recyclerView = findViewById(R.id.rvJobs)
        rvShimmer = findViewById(R.id.rvShimmer)

        recyclerView.layoutManager = LinearLayoutManager(this)
        rvShimmer.layoutManager = LinearLayoutManager(this)

        jobAdapter = JobAdapter(jobList)
        shimmerAdapter = ShimmerAdapter()

        recyclerView.adapter = jobAdapter
        rvShimmer.adapter = shimmerAdapter

        chipAll = findViewById(R.id.chipAll)
        chipDev = findViewById(R.id.chipDevelopment)
        chipDesign = findViewById(R.id.chipDesign)
        chipMarketing = findViewById(R.id.chipMarketing)

        setActiveChip(chipAll)
        loadJobs()

        // Live search typing
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyCombinedFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Chips click
        chipAll.setOnClickListener {
            currentCategory = "All"
            setActiveChip(chipAll)
            applyCombinedFilter()
        }

        chipDev.setOnClickListener {
            currentCategory = "Development"
            setActiveChip(chipDev)
            applyCombinedFilter()
        }

        chipDesign.setOnClickListener {
            currentCategory = "Designing"
            setActiveChip(chipDesign)
            applyCombinedFilter()
        }

        chipMarketing.setOnClickListener {
            currentCategory = "Marketing"
            setActiveChip(chipMarketing)
            applyCombinedFilter()
        }
    }

    private fun loadJobs() {
        db.collection("jobs")
            .whereEqualTo("status", "Active")
            .get()
            .addOnSuccessListener { documents ->
                allJobsList.clear()
                jobList.clear()

                for (doc in documents) {
                    val job = doc.toObject(JobModel::class.java)
                    allJobsList.add(job)
                }

                jobList.addAll(allJobsList)
                jobAdapter.updateList(jobList)
                tvNoResult.visibility = View.GONE
            }
    }

    // Show shimmer
    private fun showShimmer() {
        rvShimmer.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvNoResult.visibility = View.GONE
    }

    // Hide shimmer
    private fun hideShimmer() {
        rvShimmer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    // Combined search + category filter + shimmer + fade animation
    private fun applyCombinedFilter() {
        showShimmer()

        recyclerView.postDelayed({

            val searchText = etSearch.text.toString().lowercase().trim()

            val filtered = allJobsList.filter { job ->

                val matchesCategory =
                    currentCategory == "All" || job.category == currentCategory

                val title = job.title.lowercase()
                val company = job.company.lowercase()
                val location = job.location.lowercase()
                val salary = job.salary.lowercase()           // e.g. "4 LPA"
                val experience = job.experience.lowercase()   // e.g. "Fresher", "2 Years"

                val matchesSearch =
                    title.contains(searchText) ||
                            company.contains(searchText) ||
                            location.contains(searchText) ||
                            salary.contains(searchText) ||
                            experience.contains(searchText)

                matchesCategory && matchesSearch
            }

            jobList.clear()
            jobList.addAll(filtered)
            jobAdapter.updateList(jobList)

            hideShimmer()

            // Fade-in animation
            recyclerView.alpha = 0f
            recyclerView.animate()
                .alpha(1f)
                .setDuration(250)
                .start()

            if (filtered.isEmpty()) {
                tvNoResult.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                tvNoResult.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }

        }, 300)   // 300ms shimmer feel
    }

    // Chip color switching logic
    private fun setActiveChip(selectedChip: TextView) {
        val chips = listOf(chipAll, chipDev, chipDesign, chipMarketing)

        for (chip in chips) {
            chip.setBackgroundResource(R.drawable.bg_chip)
            chip.setTextColor(resources.getColor(android.R.color.black))
        }

        selectedChip.setBackgroundResource(R.drawable.bg_chip_active)
        selectedChip.setTextColor(resources.getColor(android.R.color.white))
    }
}
