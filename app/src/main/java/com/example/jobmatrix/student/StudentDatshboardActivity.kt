package com.example.jobmatrix.student

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.example.jobmatrix.profile.ProfileActivity
import com.example.jobmatrix.settings.SettingsActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import java.util.Calendar

class StudentDashboardActivity : AppCompatActivity() {

    // Navbar
    private lateinit var navProfile: LinearLayout
    private lateinit var navHome: LinearLayout
    private lateinit var navSearch: LinearLayout
    private lateinit var ivNotification: LinearLayout
    private lateinit var ivSettings: ImageView
    private lateinit var ivSavedJobs: ImageView

    // RecyclerView
    private lateinit var recyclerView: RecyclerView
    private lateinit var rvShimmer: RecyclerView
    private lateinit var jobAdapter: JobAdapter
    private lateinit var shimmerAdapter: ShimmerAdapter

    private val jobList = mutableListOf<JobModel>()
    private val db = FirebaseFirestore.getInstance()

    // Greeting
    private lateinit var tvGreeting: TextView
    private lateinit var tvUserName: TextView

    private lateinit var tvNotificationBadge: TextView
    private var notificationListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var lastNotificationCount = -1
    private lateinit var navChats: LinearLayout
    private var completionAnimationShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val skills = doc.get("skills") as? List<*>
                    if (skills.isNullOrEmpty()) {
                        startActivity(
                            Intent(this, com.example.jobmatrix.profile.SkillsActivity::class.java)
                                .putExtra("isFirstTime", true)
                        )
                        finish()
                    }
                }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        // Navbar
        navProfile = findViewById(R.id.navProfile)
        navHome = findViewById(R.id.navHome)
        navSearch = findViewById(R.id.navSearch)
        ivNotification = findViewById(R.id.ivNotification)
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge)
        listenUnreadNotifications()
        ivSettings = findViewById(R.id.ivSettings)
        ivSavedJobs = findViewById(R.id.ivSavedJobs)
        navChats = findViewById(R.id.navChats)

        setActiveNav(navHome)


        // Greeting
        tvGreeting = findViewById(R.id.tvGreeting)
        tvUserName = findViewById(R.id.tvUserName)

        setGreeting()
        loadUserName()
        loadProfileCompleteness()

        // Header entrance animation
        val headerAnim = AnimationUtils.loadAnimation(this, R.anim.anim_header_entrance)
        (findViewById<View>(R.id.ivSettings).parent as View).startAnimation(headerAnim)

        // Shimmer RecyclerView
        rvShimmer = findViewById(R.id.rvShimmer)
        rvShimmer.layoutManager = LinearLayoutManager(this)
        shimmerAdapter = ShimmerAdapter()
        rvShimmer.adapter = shimmerAdapter

        // Real RecyclerView
        recyclerView = findViewById(R.id.rvJobs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        jobAdapter = JobAdapter(jobList)
        recyclerView.adapter = jobAdapter

        showShimmer()
        loadJobs()
        loadPipelineCounts()
        setupPipelineClicks()

        findViewById<View>(R.id.completenessCard).setOnClickListener {
            startActivity(
                Intent(this, ProfileChecklistActivity::class.java)
            )
        }

        // Navbar clicks
        navHome.setOnClickListener {
            setActiveNav(navHome)
        }

        navSearch.setOnClickListener {
            setActiveNav(navSearch)
            startActivity(Intent(this, SearchActivity::class.java))
        }

        ivNotification.setOnClickListener {
            setActiveNav(ivNotification)
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        navProfile.setOnClickListener {
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
                .start()
            setActiveNav(navProfile)
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        ivSavedJobs.setOnClickListener {
            startActivity(Intent(this, SavedJobsActivity::class.java))
        }

        val navChats = findViewById<android.widget.LinearLayout>(R.id.navChats)
        navChats.setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.chat.ChatListActivity::class.java))
        }
    }

    // Show shimmer
    private fun showShimmer() {
        rvShimmer.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    // Hide shimmer
    private fun hideShimmer() {
        rvShimmer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    // Load Active Jobs with shimmer + fade animation
    private fun loadJobs() {
        showShimmer()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { userDoc ->

                val userSkills = (userDoc.get("skills") as? List<*>)?.mapNotNull {
                    it?.toString()?.trim()?.lowercase()
                } ?: emptyList()

                db.collection("jobs")
                    .whereEqualTo("status", "Active")
                    .get()
                    .addOnSuccessListener { documents ->
                        jobList.clear()

                        val sortedList = documents.mapNotNull { doc ->
                            try {
                                val job = doc.toObject(JobModel::class.java)
                                val score = calculateMatchScore(userSkills, job.skills)

                                job.copy(
                                    jobId = if (job.jobId.isBlank()) doc.id else job.jobId,
                                    matchScore = score
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedByDescending { job ->
                            when (val time = job.createdAt) {
                                is com.google.firebase.Timestamp -> time.toDate().time
                                is Long -> time
                                else -> 0L
                            }
                        }

                        jobList.addAll(sortedList)
                        jobAdapter.updateList(jobList)
                        hideShimmer()

                        recyclerView.alpha = 0f
                        recyclerView.animate()
                            .alpha(1f)
                            .setDuration(250)
                            .start()
                    }
            }
    }


    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        tvGreeting.text = greeting
    }

    private fun loadUserName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val name = document.getString("name")
                if (!name.isNullOrEmpty()) {
                    tvUserName.text = name
                }
                val photoUrl = document.getString("photoUrl")
                val ivDefault = findViewById<ImageView>(R.id.ivNavProfileDefault)
                val ivPhoto = findViewById<ImageView>(R.id.ivNavProfilePhoto)
                if (!photoUrl.isNullOrBlank()) {
                    ivDefault.visibility = View.GONE
                    ivPhoto.visibility = View.VISIBLE

                    com.bumptech.glide.Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .skipMemoryCache(true)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .into(ivPhoto)
                } else {
                    ivDefault.visibility = View.VISIBLE
                    ivPhoto.visibility = View.GONE
                }
            }
    }

    private fun loadProfileCompleteness() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->

                data class CompletionStep(
                    val title: String,
                    val points: Int,
                    val action: String
                )

                val skills = doc.get("skills") as? List<*>
                val experience = doc.getString("experience")
                val phone = doc.getString("phone")
                val photoUrl = doc.getString("photoUrl")

                var percent = 0
                val missingSteps = mutableListOf<CompletionStep>()

                if (!skills.isNullOrEmpty()) {
                    percent += 35
                } else {
                    missingSteps.add(
                        CompletionStep(
                            title = "Add your skills",
                            points = 35,
                            action = ProfileActivity.ACTION_SKILLS
                        )
                    )
                }

                if (!experience.isNullOrBlank()) {
                    percent += 25
                } else {
                    missingSteps.add(
                        CompletionStep(
                            title = "Add your experience",
                            points = 25,
                            action = ProfileActivity.ACTION_EXPERIENCE
                        )
                    )
                }

                if (!phone.isNullOrBlank()) {
                    percent += 20
                } else {
                    missingSteps.add(
                        CompletionStep(
                            title = "Add your phone number",
                            points = 20,
                            action = ProfileActivity.ACTION_PHONE
                        )
                    )
                }

                if (!photoUrl.isNullOrBlank()) {
                    percent += 20
                } else {
                    missingSteps.add(
                        CompletionStep(
                            title = "Upload a profile photo",
                            points = 20,
                            action = ProfileActivity.ACTION_PHOTO
                        )
                    )
                }

                val nextStep = missingSteps.maxByOrNull { it.points }

                val pb = findViewById<android.widget.ProgressBar>(R.id.pbCompleteness)
                val tvPercent = findViewById<TextView>(R.id.tvCompletenessPercent)
                val tvTitle = findViewById<TextView>(R.id.tvCompletenessTitle)
                val tvMessage = findViewById<TextView>(R.id.tvCompletenessMessage)
                val tvTip = findViewById<TextView>(R.id.tvCompletenessTip)
                val card = findViewById<com.google.android.material.card.MaterialCardView>(R.id.completenessCard)
                val ivCompletionAction = findViewById<ImageView>(R.id.ivCompletenessAction)

                android.animation.ValueAnimator.ofInt(pb.progress, percent).apply {
                    duration = 500
                    addUpdateListener {
                        pb.progress = it.animatedValue as Int
                    }
                    start()
                }

                tvPercent.text = "$percent%"

                val strength = when {
                    percent == 100 -> "Complete"
                    percent >= 70 -> "Strong"
                    percent >= 40 -> "Growing"
                    else -> "Basic"
                }

                val strengthColorRes = when (strength) {
                    "Complete" -> R.color.status_shortlisted_fg
                    "Strong" -> R.color.status_applied_fg
                    "Growing" -> R.color.status_review_fg
                    else -> R.color.status_rejected_fg
                }

                val strengthMessage = when (strength) {
                    "Complete" -> "Your profile is ready to apply."
                    "Strong" -> "Almost recruiter-ready."
                    "Growing" -> "You are making progress."
                    else -> "Build your profile to unlock better matches."
                }

                val strengthColor = androidx.core.content.ContextCompat.getColor(
                    this,
                    strengthColorRes
                )

                (pb.progressDrawable as? android.graphics.drawable.LayerDrawable)
                    ?.findDrawableByLayerId(android.R.id.progress)
                    ?.setTint(strengthColor)

                tvPercent.setTextColor(strengthColor)
                tvTitle.setTextColor(strengthColor)
                tvTitle.text = "Profile strength: $strength"
                tvMessage.text = strengthMessage

                if (nextStep == null) {
                    tvTip.visibility = View.GONE

                    card.contentDescription =
                        "Profile strength complete. 100 percent. $strengthMessage"
                } else {
                    tvTip.visibility = View.VISIBLE
                    tvTip.text = "Next: ${nextStep.title} · +${nextStep.points}%"

                    card.contentDescription =
                        "Profile strength $strength. $strengthMessage " +
                                "Next step: ${nextStep.title}, worth ${nextStep.points} percent."
                }

                val isComplete = nextStep == null

                val surfaceColor = androidx.core.content.ContextCompat.getColor(
                    this,
                    R.color.color_surface
                )

                card.setCardBackgroundColor(surfaceColor)

                if (isComplete) {
                    val borderWidth = (2 * resources.displayMetrics.density).toInt()

                    card.strokeWidth = borderWidth
                    card.strokeColor = strengthColor
                } else {
                    card.strokeWidth = 0
                }

                if (isComplete) {
                    ivCompletionAction.setImageResource(R.drawable.ic_check)
                    ivCompletionAction.imageTintList =
                        android.content.res.ColorStateList.valueOf(strengthColor)
                    ivCompletionAction.contentDescription = "Profile complete"

                    if (!completionAnimationShown) {
                        completionAnimationShown = true

                        card.scaleX = 0.96f
                        card.scaleY = 0.96f
                        card.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(280)
                            .start()
                    }
                } else {
                    ivCompletionAction.setImageResource(R.drawable.ic_chevron_right)

                    val hintColor = androidx.core.content.ContextCompat.getColor(
                        this,
                        R.color.color_text_hint
                    )

                    ivCompletionAction.imageTintList =
                        android.content.res.ColorStateList.valueOf(hintColor)

                    ivCompletionAction.contentDescription = "Complete profile"
                }

//                card.setOnClickListener {
//                    card.setOnClickListener {
//                        startActivity(
//                            Intent(this, ProfileChecklistActivity::class.java)
//                        )
//                    }
//                }
            }
    }

    private fun listenUnreadNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        notificationListener = db.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    android.util.Log.e(
                        "JM_NOTIFICATION",
                        "Student badge listener failed",
                        error
                    )
                    return@addSnapshotListener
                }

                val count = snapshot?.documents?.count {
                    !(it.getString("type")?.equals("Applied", ignoreCase = true) ?: false)
                } ?: 0

                tvNotificationBadge.text =
                    if (count > 9) "9+" else count.toString()

                tvNotificationBadge.visibility =
                    if (count > 0) View.VISIBLE else View.GONE

                if (count != lastNotificationCount && count > 0) {
                    tvNotificationBadge.scaleX = 0.7f
                    tvNotificationBadge.scaleY = 0.7f
                    tvNotificationBadge.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .start()
                }

                lastNotificationCount = count
            }
    }

    override fun onResume() {
        super.onResume()

        if (notificationListener == null) {
            listenUnreadNotifications()
        }

        setActiveNav(navHome)

        // Refresh data changed from ProfileActivity
        loadUserName()
        loadProfileCompleteness()
        loadPipelineCounts()
        loadJobs()
    }

    private fun setActiveNav(selected: LinearLayout) {
        val navItems = listOf(navHome, navSearch, ivNotification, navChats, navProfile)
        for (item in navItems) item.isSelected = false
        selected.isSelected = true
    }

    private fun loadPipelineCounts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("applications").whereEqualTo("studentId", uid)
            .get()
            .addOnSuccessListener { docs ->
                val applied = docs.count { it.getString("status") == "Applied" }
                val shortlisted = docs.count { it.getString("status") == "Shortlisted" }
                val inReview = docs.count { it.getString("status") == "In Review" }

                findViewById<TextView>(R.id.tvAppliedCount).text = applied.toString()
                findViewById<TextView>(R.id.tvInReviewCount).text = inReview.toString()
                findViewById<TextView>(R.id.tvShortlistedCount).text = shortlisted.toString()
            }
    }

    private fun calculateMatchScore(
        userSkills: List<String>,
        jobSkills: List<String>
    ): Int {
        if (userSkills.isEmpty() || jobSkills.isEmpty()) return 0

        val userSet = userSkills.map { it.trim().lowercase() }.toSet()
        val jobSet = jobSkills.map { it.trim().lowercase() }.toSet()

        val matchedCount = jobSet.count { it in userSet }
        return ((matchedCount.toFloat() / jobSet.size) * 100).toInt()
    }


    private fun animateAndOpen(view: View, status: String) {
        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .withEndAction {
                        startActivity(
                            Intent(this, MyApplicationsActivity::class.java)
                                .putExtra("statusFilter", status)
                        )
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }.start()
            }.start()
    }

    private fun setupPipelineClicks() {
        val cardApplied = findViewById<TextView>(R.id.tvAppliedCount).parent as View
        val cardInReview = findViewById<TextView>(R.id.tvInReviewCount).parent as View
        val cardShortlisted = findViewById<TextView>(R.id.tvShortlistedCount).parent as View

        cardApplied.setOnClickListener { animateAndOpen(it, "Applied") }
        cardInReview.setOnClickListener { animateAndOpen(it, "In Review") }
        cardShortlisted.setOnClickListener { animateAndOpen(it, "Shortlisted") }
    }

    override fun onStop() {
        super.onStop()
        notificationListener?.remove()
        notificationListener = null
    }
}