package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
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
import android.widget.LinearLayout
import kotlinx.coroutines.launch

class EmployerApplicationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerApplicationAdapter
    private val allData = mutableListOf<AppWithJob>()
    private val displayedData = mutableListOf<AppWithJob>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentFilter = "All"

    private lateinit var tabAll: TextView
    private lateinit var tabApplied: TextView
    private lateinit var tabInReview: TextView
    private lateinit var tabShortlisted: TextView
    private lateinit var tabRejected: TextView
    private var selectedJobFilter: String? = null
    private var sortNewestFirst = true

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_applications)

        val filterJobId = intent.getStringExtra("jobId")
        val filterStatus = intent.getStringExtra("filterStatus") ?: "All"

        recyclerView = findViewById(R.id.rvApplications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmployerApplicationAdapter(displayedData) { item ->
            val i = Intent(this, ApplicantDetailsActivity::class.java)
            i.putExtra("applicationId", item.app.applicationId)
            i.putExtra("studentId", item.app.studentId)
            i.putExtra("jobTitle", item.job?.title ?: item.app.jobTitle)
            i.putExtra("companyName", item.job?.company ?: "")
            i.putExtra("status", item.app.status)
            i.putExtra("resumeLink", item.app.resumeLink)
            i.putExtra("appliedAt", item.app.appliedAt)
            i.putExtra("jobLocation", item.job?.location ?: "")
            i.putStringArrayListExtra("jobSkills", ArrayList(item.job?.skills ?: emptyList()))
            startActivity(i)
        }
        recyclerView.adapter = adapter

        val selectionToolbar = findViewById<LinearLayout>(R.id.selectionToolbar)
        val tvSelectedCount = findViewById<TextView>(R.id.tvSelectedCount)

        adapter.onSelectionChanged = { count ->
            tvSelectedCount.text = "$count selected"
            if (count > 0 && selectionToolbar.visibility != View.VISIBLE) {
                selectionToolbar.visibility = View.VISIBLE
                selectionToolbar.alpha = 0f
                selectionToolbar.translationY = -20f
                selectionToolbar.animate().alpha(1f).translationY(0f).setDuration(200).start()
            } else if (count == 0) {
                selectionToolbar.animate().alpha(0f).translationY(-20f).setDuration(150)
                    .withEndAction { selectionToolbar.visibility = View.GONE }.start()
            }
        }

        findViewById<ImageView>(R.id.btnCancelSelection).setOnClickListener {
            adapter.exitSelectionMode()
            selectionToolbar.visibility = View.GONE
        }

        findViewById<TextView>(R.id.btnBulkInReview).setOnClickListener { bulkUpdateStatus("In Review") }
        findViewById<TextView>(R.id.btnBulkShortlist).setOnClickListener { bulkUpdateStatus("Shortlisted") }
        findViewById<TextView>(R.id.btnBulkReject).setOnClickListener { bulkUpdateStatus("Rejected") }

        tabAll = findViewById(R.id.tabAll)
        tabApplied = findViewById(R.id.tabApplied)
        tabInReview = findViewById(R.id.tabInReview)
        tabShortlisted = findViewById(R.id.tabShortlisted)
        tabRejected = findViewById(R.id.tabRejected)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.ivFilter).setOnClickListener { showFilterSheet() }

        tabAll.setOnClickListener { setFilter("All") }
        tabApplied.setOnClickListener { setFilter("Applied") }
        tabInReview.setOnClickListener { setFilter("In Review") }
        tabShortlisted.setOnClickListener { setFilter("Shortlisted") }
        tabRejected.setOnClickListener { setFilter("Rejected") }

        loadData(filterJobId) {
            // auto-select tab after data loads
            if (filterStatus != "All") setFilter(filterStatus)
        }
    }

    override fun onResume() {
        super.onResume()
        if (allData.isNotEmpty()) applyFilter()
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        val tabs = listOf(
            tabAll to "All", tabApplied to "Applied",
            tabInReview to "In Review", tabShortlisted to "Shortlisted", tabRejected to "Rejected"
        )
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

    @SuppressLint("NotifyDataSetChanged")
    private fun applyFilter() {
        val filtered = allData.filter {
            (currentFilter == "All" || it.app.status.equals(currentFilter, ignoreCase = true)) &&
                    (selectedJobFilter == null || it.job?.title == selectedJobFilter)
        }.let { list ->
            if (sortNewestFirst) list.sortedByDescending { it.app.appliedAt }
            else list.sortedBy { it.app.appliedAt }
        }
        displayedData.clear()
        displayedData.addAll(filtered)
        adapter.notifyDataSetChanged()
        updateTabCounts()
    }

    @SuppressLint("SetTextI18n")
    private fun updateTabCounts() {
        tabAll.text = "All (${allData.size})"
        tabApplied.text = "Applied (${allData.count { it.app.status.equals("Applied", true) }})"
        tabInReview.text = "In Review (${allData.count { it.app.status.equals("In Review", true) }})"
        tabShortlisted.text = "Shortlisted (${allData.count { it.app.status.equals("Shortlisted", true) }})"
        tabRejected.text = "Rejected (${allData.count { it.app.status.equals("Rejected", true) }})"
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData(filterJobId: String?, onLoaded: () -> Unit = {}) {
        val employerId = auth.currentUser?.uid ?: return
        db.collection("jobs").whereEqualTo("employerId", employerId).get()
            .addOnSuccessListener { jobDocs ->
                val jobMap = HashMap<String, JobModel>()
                for (doc in jobDocs) doc.toObject(JobModel::class.java).let { jobMap[it.jobId] = it }

                val jobIds = if (filterJobId != null) listOf(filterJobId) else jobMap.keys.toList()
                if (jobIds.isEmpty()) { applyFilter(); onLoaded(); return@addOnSuccessListener }

                db.collection("applications").whereIn("jobId", jobIds.take(30)).get()
                    .addOnSuccessListener { appDocs ->
                        allData.clear()
                        for (doc in appDocs) {
                            val app = doc.toObject(ApplicationModel::class.java).copy(applicationId = doc.id)
                            allData.add(AppWithJob(app, jobMap[app.jobId]))
                        }
                        applyFilter()
                        onLoaded()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load applications", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun showFilterSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_filter_applications, null)
        dialog.setContentView(view)

        val chipGroupJobs = view.findViewById<android.widget.LinearLayout>(R.id.chipGroupJobs)
        val jobTitles = allData.mapNotNull { it.job?.title }.distinct()
        val chipViews = mutableListOf<TextView>()

        fun addChip(label: String) {
            val chip = TextView(this).apply {
                text = label
                setPadding(32, 16, 32, 16)
                textSize = 12f
                setTextColor(resources.getColor(
                    if (label == selectedJobFilter || (selectedJobFilter == null && label == "All"))
                        android.R.color.white else R.color.color_text_secondary, theme))
                background = resources.getDrawable(
                    if (label == selectedJobFilter || (selectedJobFilter == null && label == "All"))
                        R.drawable.bg_chip_active else R.drawable.bg_chip, theme)
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 16
                layoutParams = lp
                setOnClickListener {
                    selectedJobFilter = if (label == "All") null else label
                    chipViews.forEach {
                        val active = it.text == label
                        it.setTextColor(resources.getColor(if (active) android.R.color.white else R.color.color_text_secondary, theme))
                        it.background = resources.getDrawable(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip, theme)
                    }
                }
            }
            chipViews.add(chip)
            chipGroupJobs.addView(chip)
        }

        addChip("All")
        jobTitles.forEach { addChip(it) }

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
            selectedJobFilter = null; sortNewestFirst = true; applyFilter(); dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnApplyFilter).setOnClickListener {
            applyFilter(); dialog.dismiss()
        }
        dialog.show()
    }

    private fun bulkUpdateStatus(newStatus: String) {
        val selectedIds = adapter.getSelectedApplicationIds()
        if (selectedIds.isEmpty()) return

        android.app.AlertDialog.Builder(this)
            .setTitle("$newStatus ${selectedIds.size} applicant(s)?")
            .setPositiveButton("Confirm") { _, _ ->
                val timestampField = when (newStatus) {
                    "In Review" -> "inReviewAt"
                    "Shortlisted" -> "shortlistedAt"
                    "Rejected" -> "rejectedAt"
                    else -> null
                }

                val batch = db.batch()
                val affectedApps = allData.filter { selectedIds.contains(it.app.applicationId) }

                for (item in affectedApps) {
                    val updates = mutableMapOf<String, Any>("status" to newStatus)
                    if (timestampField != null) updates[timestampField] = System.currentTimeMillis()
                    batch.update(
                        db.collection("applications").document(item.app.applicationId),
                        updates
                    )
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Updated $newStatus for ${affectedApps.size} applicant(s)", Toast.LENGTH_SHORT).show()
                        for (item in affectedApps) {
                            sendBulkNotification(item, newStatus)
                        }
                        adapter.exitSelectionMode()
                        findViewById<LinearLayout>(R.id.selectionToolbar).visibility = View.GONE
                        loadData(intent.getStringExtra("jobId"))
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Bulk update failed", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendBulkNotification(item: AppWithJob, newStatus: String) {
        val jobTitle = item.job?.title ?: item.app.jobTitle
        val companyName = item.job?.company ?: ""
        val message = when (newStatus) {
            "Shortlisted" -> "You have been shortlisted for $jobTitle at $companyName."
            "In Review" -> "Your application for $jobTitle at $companyName is under review."
            else -> "Your application for $jobTitle at $companyName was not selected."
        }

        val notif = hashMapOf(
            "studentId" to item.app.studentId,
            "recipientId" to item.app.studentId,
            "applicationId" to item.app.applicationId,
            "jobTitle" to jobTitle,
            "companyName" to companyName,
            "message" to message,
            "type" to newStatus,
            "createdAt" to System.currentTimeMillis(),
            "isRead" to false
        )
        db.collection("notifications").add(notif)

        db.collection("users").document(item.app.studentId).get()
            .addOnSuccessListener { doc ->
                val token = doc.getString("fcmToken") ?: ""
                if (token.isNotBlank()) {
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            com.example.jobmatrix.network.RetrofitClient.api.sendNotification(
                                com.example.jobmatrix.network.NotifyRequest(token, "JobMatrix", message)
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("JM_BULK", "Push failed", e)
                        }
                    }
                }
            }
    }
}