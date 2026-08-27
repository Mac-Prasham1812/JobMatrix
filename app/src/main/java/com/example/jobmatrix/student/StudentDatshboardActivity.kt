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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val skills = doc.get("skills") as? List<*>
                    if (skills.isNullOrEmpty()) {
                        startActivity(Intent(this, com.example.jobmatrix.profile.SkillsActivity::class.java)
                            .putExtra("isFirstTime", true))
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

                val count = snapshot?.size() ?: 0

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
                        startActivity(Intent(this, MyApplicationsActivity::class.java)
                            .putExtra("statusFilter", status))
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