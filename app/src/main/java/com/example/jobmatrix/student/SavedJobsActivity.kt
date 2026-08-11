package com.example.jobmatrix.student

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

data class SavedJobItem(val job: JobModel, val savedAt: Long)

class SavedJobsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val allSaved = mutableListOf<SavedJobItem>()
    private val jobList = mutableListOf<JobModel>()
    private lateinit var jobAdapter: JobAdapter

    private lateinit var rvShimmer: RecyclerView
    private lateinit var rvSavedJobs: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvSavedCount: TextView

    private var matchFilter = "All"
    private var sortNewestFirst = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_jobs)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivFilter).setOnClickListener { showFilterSheet() }

        rvShimmer = findViewById(R.id.rvShimmer)
        rvShimmer.layoutManager = LinearLayoutManager(this)
        rvShimmer.adapter = ShimmerAdapter()

        rvSavedJobs = findViewById(R.id.rvSavedJobs)
        rvSavedJobs.layoutManager = LinearLayoutManager(this)
        jobAdapter = JobAdapter(jobList)
        rvSavedJobs.adapter = jobAdapter

        emptyState = findViewById(R.id.emptyState)
        tvSavedCount = findViewById(R.id.tvSavedCount)
    }

    override fun onResume() {
        super.onResume()
        loadSavedJobs()
    }

    private fun loadSavedJobs() {
        showShimmer()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val savedMap = userDoc.get("savedJobs") as? Map<*, *> ?: emptyMap<String, Any>()
                val userSkills = (userDoc.get("skills") as? List<*>)
                    ?.mapNotNull { it?.toString()?.trim()?.lowercase() } ?: emptyList()

                if (savedMap.isEmpty()) {
                    allSaved.clear(); applyFilter(); return@addOnSuccessListener
                }

                val savedIds = savedMap.keys.mapNotNull { it as? String }

                db.collection("jobs").whereIn("jobId", savedIds.take(30)).get()
                    .addOnSuccessListener { docs ->
                        allSaved.clear()
                        for (doc in docs) {
                            val job = doc.toObject(JobModel::class.java)
                            val score = calculateMatchScore(userSkills, job.skills)
                            val finalJob = job.copy(jobId = job.jobId.ifBlank { doc.id }, matchScore = score)
                            val ts = (savedMap[finalJob.jobId] as? Timestamp)?.toDate()?.time ?: 0L
                            allSaved.add(SavedJobItem(finalJob, ts))
                        }
                        applyFilter()
                    }
                    .addOnFailureListener { showEmpty() }
            }
            .addOnFailureListener { showEmpty() }
    }

    private fun applyFilter() {
        var filtered = allSaved.filter { item ->
            when (matchFilter) {
                "80" -> item.job.matchScore >= 80
                "50" -> item.job.matchScore >= 50
                "below50" -> item.job.matchScore < 50
                else -> true
            }
        }
        filtered = if (sortNewestFirst) filtered.sortedByDescending { it.savedAt }
        else filtered.sortedBy { it.savedAt }

        jobList.clear()
        jobList.addAll(filtered.map { it.job })
        jobAdapter.updateList(jobList)
        tvSavedCount.text = "${jobList.size} saved"

        if (jobList.isEmpty()) showEmpty() else showList()

        rvSavedJobs.alpha = 0f
        rvSavedJobs.animate().alpha(1f).setDuration(250).start()
    }

    private fun calculateMatchScore(userSkills: List<String>, jobSkills: List<String>): Int {
        if (userSkills.isEmpty() || jobSkills.isEmpty()) return 0
        val jobSet = jobSkills.map { it.trim().lowercase() }.toSet()
        val matched = jobSet.count { it in userSkills }
        return ((matched.toFloat() / jobSet.size) * 100).toInt()
    }

    private fun showShimmer() {
        rvShimmer.visibility = View.VISIBLE
        rvSavedJobs.visibility = View.GONE
        emptyState.visibility = View.GONE
    }

    private fun showList() {
        rvShimmer.visibility = View.GONE
        rvSavedJobs.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun showEmpty() {
        rvShimmer.visibility = View.GONE
        rvSavedJobs.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        tvSavedCount.text = "0 saved"
    }

    private fun showFilterSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_saved_jobs_filter, null)
        dialog.setContentView(view)

        val chipAll = view.findViewById<TextView>(R.id.chipMatchAll)
        val chip80 = view.findViewById<TextView>(R.id.chipMatch80)
        val chip50 = view.findViewById<TextView>(R.id.chipMatch50)
        val chipBelow50 = view.findViewById<TextView>(R.id.chipMatchBelow50)
        val matchChips = mapOf(chipAll to "All", chip80 to "80", chip50 to "50", chipBelow50 to "below50")

        fun refreshMatchChips() {
            matchChips.forEach { (chip, key) ->
                val active = key == matchFilter
                chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
                chip.setTextColor(resources.getColor(if (active) android.R.color.white else R.color.color_text_secondary, theme))
            }
        }
        refreshMatchChips()
        matchChips.forEach { (chip, key) -> chip.setOnClickListener { matchFilter = key; refreshMatchChips() } }

        val chipNewest = view.findViewById<TextView>(R.id.chipSortNewest)
        val chipOldest = view.findViewById<TextView>(R.id.chipSortOldest)
        fun refreshSortChips() {
            chipNewest.setBackgroundResource(if (sortNewestFirst) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chipNewest.setTextColor(resources.getColor(if (sortNewestFirst) android.R.color.white else R.color.color_text_secondary, theme))
            chipOldest.setBackgroundResource(if (!sortNewestFirst) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chipOldest.setTextColor(resources.getColor(if (!sortNewestFirst) android.R.color.white else R.color.color_text_secondary, theme))
        }
        refreshSortChips()
        chipNewest.setOnClickListener { sortNewestFirst = true; refreshSortChips() }
        chipOldest.setOnClickListener { sortNewestFirst = false; refreshSortChips() }

        view.findViewById<View>(R.id.btnResetFilter).setOnClickListener {
            matchFilter = "All"; sortNewestFirst = true; applyFilter(); dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnApplyFilter).setOnClickListener {
            applyFilter(); dialog.dismiss()
        }
        dialog.show()
    }
}