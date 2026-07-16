package com.example.jobmatrix.student

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class SearchActivity : AppCompatActivity() {

    private var filterLocation = ""
    private var filterExperience = "Any"
    private var filterMinSalary = 0f
    private var filterMaxSalary = 15f

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
    private var userSkills: List<String> = emptyList()



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

        findViewById<ImageView>(R.id.ivFilterIcon).setOnClickListener {
            showFilterBottomSheet()
        }
    }


    private fun loadJobs() {
        showShimmer()

        db.collection("jobs")
            .whereEqualTo("status", "Active")
            .get()
            .addOnSuccessListener { documents ->
                allJobsList.clear()
                jobList.clear()

                for (doc in documents) {
                    val job = doc.toObject(JobModel::class.java)
                    allJobsList.add(job.copy(
                        jobId = if (job.jobId.isBlank()) doc.id else job.jobId,
                        matchScore = calculateMatchScore(job.skills)
                    ))
                }

                allJobsList.sortByDescending { job ->
                    when (val time = job.createdAt) {
                        is com.google.firebase.Timestamp -> time.toDate().time
                        is Long -> time
                        else -> 0L
                    }
                }

                jobList.addAll(allJobsList)
                jobAdapter.updateList(jobList)

                hideShimmer()
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

                val matchesLocation = filterLocation.isEmpty() || job.location.lowercase().contains(filterLocation.lowercase())
                val salary = job.salary.lowercase()           // e.g. "4 LPA"

                val salaryNum = job.salary.filter { it.isDigit() || it == '.' }.toFloatOrNull() ?: 0f
                val matchesSalary = salaryNum in filterMinSalary..filterMaxSalary
                val experience = job.experience.lowercase()   // e.g. "Fresher", "2 Years"

                val matchesExperience = filterExperience == "Any" ||
                        (filterExperience == "Fresher" && job.experience.contains("Fresher", ignoreCase = true)) ||
                        (filterExperience == "1 Year" && job.experience.contains("1", ignoreCase = true)) ||
                        (filterExperience == "2+ Years" && (job.experience.contains("2") || job.experience.contains("3") || job.experience.contains("4") || job.experience.contains("5")))


                val matchesSearch =
                    title.contains(searchText) ||
                            company.contains(searchText) ||
                            location.contains(searchText) ||
                            salary.contains(searchText) ||
                            experience.contains(searchText)

                matchesCategory && matchesSearch && matchesLocation && matchesExperience && matchesSalary
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
            chip.setTextColor(resources.getColor(R.color.color_text_secondary, theme))
        }

        selectedChip.setBackgroundResource(R.drawable.bg_chip_active)
        selectedChip.setTextColor(resources.getColor(android.R.color.white, theme))
    }


    private fun showFilterBottomSheet() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottomsheet_filter, null)
        sheet.setContentView(view)

        val etLoc = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etFilterLocation)
        val slider = view.findViewById<com.google.android.material.slider.RangeSlider>(R.id.salarySlider)
        val tvLabel = view.findViewById<TextView>(R.id.tvSalaryRangeLabel)
        val chipAny = view.findViewById<TextView>(R.id.chipExpAny)
        val chipFresher = view.findViewById<TextView>(R.id.chipExpFresher)
        val chip1 = view.findViewById<TextView>(R.id.chipExp1)
        val chip2Plus = view.findViewById<TextView>(R.id.chipExp2Plus)

        etLoc.setText(filterLocation)
        slider.setValues(filterMinSalary, filterMaxSalary)
        tvLabel.text = "Salary Range: ${filterMinSalary.toInt()} - ${filterMaxSalary.toInt()} LPA"

        val expChips = listOf(chipAny, chipFresher, chip1, chip2Plus)
        fun setExpChip(selected: TextView) {
            for (c in expChips) { c.setBackgroundResource(R.drawable.bg_chip); c.setTextColor(resources.getColor(R.color.color_text_secondary, theme)) }
            selected.setBackgroundResource(R.drawable.bg_chip_active)
            selected.setTextColor(resources.getColor(android.R.color.white, theme))
        }
        when (filterExperience) {
            "Fresher" -> setExpChip(chipFresher)
            "1 Year" -> setExpChip(chip1)
            "2+ Years" -> setExpChip(chip2Plus)
            else -> setExpChip(chipAny)
        }

        chipAny.setOnClickListener { filterExperience = "Any"; setExpChip(chipAny) }
        chipFresher.setOnClickListener { filterExperience = "Fresher"; setExpChip(chipFresher) }
        chip1.setOnClickListener { filterExperience = "1 Year"; setExpChip(chip1) }
        chip2Plus.setOnClickListener { filterExperience = "2+ Years"; setExpChip(chip2Plus) }

        slider.addOnChangeListener { s, _, _ ->
            val vals = s.values
            tvLabel.text = "Salary Range: ${vals[0].toInt()} - ${vals[1].toInt()} LPA"
        }

        view.findViewById<View>(R.id.btnResetFilter).setOnClickListener {
            filterLocation = ""
            filterExperience = "Any"
            filterMinSalary = 0f
            filterMaxSalary = 15f
            applyCombinedFilter()
            sheet.dismiss()
        }

        view.findViewById<View>(R.id.btnApplyFilter).setOnClickListener {
            filterLocation = etLoc.text.toString().trim()
            val vals = slider.values
            filterMinSalary = vals[0]
            filterMaxSalary = vals[1]
            applyCombinedFilter()
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun loadUserSkillsThenJobs() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { loadJobs(); return }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userSkills = (doc.get("skills") as? List<*>)?.mapNotNull {
                    it?.toString()?.trim()?.lowercase()
                } ?: emptyList()
                loadJobs()
            }
            .addOnFailureListener { loadJobs() }
    }

    private fun calculateMatchScore(jobSkills: List<String>): Int {
        if (userSkills.isEmpty() || jobSkills.isEmpty()) return 0
        val jobSet = jobSkills.map { it.trim().lowercase() }.toSet()
        val matched = jobSet.count { it in userSkills.toSet() }
        return ((matched.toFloat() / jobSet.size) * 100).toInt()
    }

    override fun onResume() {
        super.onResume()
        loadUserSkillsThenJobs()
    }

}
