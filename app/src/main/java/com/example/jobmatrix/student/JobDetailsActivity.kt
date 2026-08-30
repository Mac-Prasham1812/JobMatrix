package com.example.jobmatrix.student

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.model.JobModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class JobDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvTitle: TextView
    private lateinit var tvCompanyOverview: TextView
    private lateinit var tvExperience: com.google.android.material.chip.Chip
    private lateinit var tvCategory: com.google.android.material.chip.Chip
    private lateinit var tvSalary: TextView
    private lateinit var btnApply: Button
    private lateinit var tvCompany: TextView
    private lateinit var tvLocation: com.google.android.material.chip.Chip
    private lateinit var tvMatchBadge: com.google.android.material.chip.Chip
    private lateinit var tvPostedOn: TextView
    private lateinit var chipGroupJobSkills: com.google.android.material.chip.ChipGroup
    private lateinit var btnSaveJob: com.google.android.material.button.MaterialButton
    private lateinit var tvApplicants: TextView

    private var jobId: String? = null
    private var currentJob: JobModel? = null
    private var passedMatchScore: Int = 0
    private var isJobSaved = false
    private var existingApplicationId: String = ""
    private var existingApplication: com.example.jobmatrix.model.ApplicationModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_details)

        tvTitle = findViewById(R.id.tvTitle)
        tvCompany = findViewById(R.id.tvCompany)
        tvLocation = findViewById(R.id.tvLocation)
        tvCompanyOverview = findViewById(R.id.tvCompanyOverview)
        tvCategory = findViewById(R.id.tvCategory)
        tvExperience = findViewById(R.id.tvExperience)
        tvSalary = findViewById(R.id.tvSalary)
        btnApply = findViewById(R.id.btnApply)
        tvMatchBadge = findViewById(R.id.tvMatchBadge)
        tvPostedOn = findViewById(R.id.tvPostedOn)
        addPressEffect(btnApply)

        btnSaveJob = findViewById(R.id.btnSaveJob)
        addPressEffect(btnSaveJob)
        btnSaveJob.setOnClickListener { toggleSaveJob() }

        passedMatchScore = intent.getIntExtra("matchScore", 0)
        tvApplicants = findViewById(R.id.tvApplicants)

        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener { finish() }
        chipGroupJobSkills = findViewById(R.id.chipGroupJobSkills)




        jobId = intent.getStringExtra("jobId")

        if (jobId.isNullOrEmpty()) {
            showToast("Job not found")
            finish()
            return
        }





        // Redirect to ApplyJobActivity
        btnApply.setOnClickListener {
            val intent = Intent(this, ApplyJobActivity::class.java)
            intent.putExtra("jobId", jobId)
            intent.putExtra("jobTitle", currentJob?.title ?: "")
            intent.putExtra("companyName", currentJob?.company ?: "")
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        jobId?.let {
            loadJobDetails(it)
            checkSavedState(it)
        }
    }

    private fun loadJobDetails(jobId: String) {
        db.collection("jobs")
            .document(jobId)
            .get()
            .addOnSuccessListener { doc ->
                val job = doc.toObject(JobModel::class.java)
                if (job != null) {
                    currentJob = job
                    bindJob(job)
                    animateContent()
                    checkExistingApplication(jobId)
                }
            }
            .addOnFailureListener {
                showToast("Failed to load job")
            }
    }

    @SuppressLint("SetTextI18n", "CutPasteId")
    private fun bindJob(job: JobModel) {
        tvTitle.text = job.title
        tvCompany.text = job.company
        tvLocation.text = job.location
        tvCompanyOverview.text = if (job.companyOverview.isNotEmpty()) job.companyOverview else "Company information not provided."
        tvCategory.text = job.category
        tvExperience.text = job.experience
        tvSalary.text = "₹${job.salary}"
        tvMatchBadge.text = "${job.matchScore}% match"
        styleMatchBadge(job.matchScore)
        findViewById<TextView>(R.id.tvExperienceCard).text = job.experience
        findViewById<TextView>(R.id.tvCategoryCard).text = job.category


        tvApplicants.text = job.applicantsCount.toString()

        val badgeColors = listOf(R.color.badge_purple, R.color.badge_green, R.color.badge_teal, R.color.badge_orange)
        findViewById<TextView>(R.id.tvCompanyInitials).text = getInitials(job.company)
        val bgDrawable = findViewById<TextView>(R.id.tvCompanyInitials).background.mutate() as android.graphics.drawable.GradientDrawable
        bgDrawable.setColor(androidx.core.content.ContextCompat.getColor(this, badgeColors[Math.abs(job.company.hashCode()) % badgeColors.size]))


        val createdMillis = when (val t = job.createdAt) {
            is com.google.firebase.Timestamp -> t.toDate().time
            is Long -> t
            else -> System.currentTimeMillis()
        }
        val days = ((System.currentTimeMillis() - createdMillis) / (1000 * 60 * 60 * 24)).toInt()
        tvPostedOn.text = if (days <= 0) "Today" else "$days d ago"


        chipGroupJobSkills.removeAllViews()
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        db.collection("users").document(myUid ?: "").get().addOnSuccessListener { userDoc ->
            val mySkills = (userDoc.get("skills") as? List<*>)?.map { it.toString().lowercase() } ?: emptyList()

            val jobSkillsLower = job.skills.map { it.lowercase() }
            val matched = jobSkillsLower.count { mySkills.contains(it) }
            val liveScore = if (job.skills.isEmpty()) 0 else ((matched.toFloat() / job.skills.size) * 100).toInt()
            tvMatchBadge.text = "$liveScore% match"
            styleMatchBadge(liveScore)

            for (skill in job.skills) {
                val has = mySkills.contains(skill.lowercase())
                val chip = com.google.android.material.chip.Chip(this).apply {
                    text = skill
                    isClickable = !has
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor(if (has) "#DCFCE7" else "#FEE2E2"))
                    setTextColor(android.graphics.Color.parseColor(if (has) "#16A34A" else "#DC2626"))
                    if (!has) {
                        setOnClickListener {
                            startActivity(Intent(this@JobDetailsActivity, com.example.jobmatrix.profile.SkillsActivity::class.java))
                        }
                    }
                }
                chipGroupJobSkills.addView(chip)
            }
        }
    }

    private fun animateContent() {
        val views = listOf(
            findViewById<android.view.View>(R.id.tvTitle),
            findViewById<android.view.View>(R.id.tvCompany),
            findViewById<android.view.View>(R.id.tvLocation),
            findViewById<android.view.View>(R.id.tvCompanyOverview),
            findViewById<android.view.View>(R.id.tvExperience),
            findViewById<android.view.View>(R.id.tvCategory),
            findViewById<android.view.View>(R.id.tvPostedOn),
            findViewById<android.view.View>(R.id.tvSalary),
            findViewById<android.view.View>(R.id.btnSaveJob),
            findViewById<android.view.View>(R.id.btnApply)
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 24f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * 45).toLong())
                .setDuration(260)
                .start()
        }
    }

    private fun styleMatchBadge(score: Int) {
        val bg = when {
            score >= 80 -> "#DCFCE7"
            score >= 50 -> "#FEF3C7"
            else -> "#FEE2E2"
        }
        val text = when {
            score >= 80 -> "#16A34A"
            score >= 50 -> "#D97706"
            else -> "#DC2626"
        }

        tvMatchBadge.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(bg))
        tvMatchBadge.setTextColor(android.graphics.Color.parseColor(text))
    }

    private fun getInitials(company: String): String {
        val words = company.trim().split(" ").filter { it.isNotEmpty() }
        return if (words.size >= 2) (words[0].first().toString() + words[1].first()
            .toString()).uppercase()
        else company.take(2).uppercase()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addPressEffect(view: android.view.View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
            false
        }
    }
    private fun checkSavedState(jobId: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val savedMap = doc.get("savedJobs") as? Map<*, *>
                isJobSaved = savedMap?.containsKey(jobId) == true
                updateSaveButtonUI()
            }
    }

    private fun toggleSaveJob() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val id = jobId ?: return
        val userRef = db.collection("users").document(uid)

        val update: Any = if (isJobSaved)
            com.google.firebase.firestore.FieldValue.delete()
        else
            com.google.firebase.Timestamp.now()

        userRef.update("savedJobs.$id", update)
            .addOnSuccessListener {
                isJobSaved = !isJobSaved
                updateSaveButtonUI()
                showToast(if (isJobSaved) "Job saved" else "Removed from saved")
            }
            .addOnFailureListener {
                // field may not exist yet on first save
                if (!isJobSaved) {
                    userRef.update("savedJobs", mapOf(id to com.google.firebase.Timestamp.now()))
                        .addOnSuccessListener {
                            isJobSaved = true
                            updateSaveButtonUI()
                            showToast("Job saved")
                        }
                } else {
                    showToast("Failed to update saved job")
                }
            }
    }

    private fun updateSaveButtonUI() {
        btnSaveJob.text = if (isJobSaved) "Saved" else "Save Job"
    }

    private fun showToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.toast_custom, null)
        layout.findViewById<TextView>(R.id.tvToastMessage).text = message
        Toast(this).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
            show()
        }
    }

    private fun checkExistingApplication(jobId: String) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("applications")
            .whereEqualTo("studentId", uid)
            .whereEqualTo("jobId", jobId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    existingApplicationId = doc.id
                    existingApplication = doc.toObject(com.example.jobmatrix.model.ApplicationModel::class.java)
                        ?.copy(applicationId = doc.id)
                    showAppliedState()
                }
            }
    }

    private fun showAppliedState() {
        val app = existingApplication ?: return
        btnApply.text = "View Application Status"
        btnApply.setOnClickListener {
            val intent = Intent(this, com.example.jobmatrix.student.ApplicationTimelineActivity::class.java)
            intent.putExtra("jobTitle", app.jobTitle)
            intent.putExtra("companyName", app.companyName)
            intent.putExtra("status", app.status)
            intent.putExtra("appliedAt", app.appliedAt)
            intent.putExtra("inReviewAt", app.inReviewAt)
            intent.putExtra("shortlistedAt", app.shortlistedAt)
            intent.putExtra("rejectedAt", app.rejectedAt)
            startActivity(intent)
        }
    }
}