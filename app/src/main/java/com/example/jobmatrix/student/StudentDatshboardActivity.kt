package com.example.jobmatrix.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.example.jobmatrix.profile.ProfileActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import java.util.Calendar

class StudentDashboardActivity : AppCompatActivity() {

    // Navbar
    private lateinit var navProfile: ImageView
    private lateinit var navHome: ImageView
    private lateinit var navSearch: ImageView
    private lateinit var ivNotification: ImageView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        // Navbar
        navProfile = findViewById(R.id.navProfile)
        navHome = findViewById(R.id.navHome)
        navSearch = findViewById(R.id.navSearch)
        ivNotification = findViewById(R.id.ivNotification)

        setActiveNav(navHome)

        // Greeting
        tvGreeting = findViewById(R.id.tvGreeting)
        tvUserName = findViewById(R.id.tvUserName)

        setGreeting()
        loadUserName()

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
        checkNotifications()

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

        db.collection("jobs")
            .whereEqualTo("status", "Active")
            .get()
            .addOnSuccessListener { documents ->
                jobList.clear()

                val sortedList = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(JobModel::class.java)
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

                // Fade-in effect
                recyclerView.alpha = 0f
                recyclerView.animate()
                    .alpha(1f)
                    .setDuration(250)
                    .start()
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

    private fun checkNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("applications")
            .whereEqualTo("studentId", userId)
            .whereEqualTo("hasNotification", true)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { _, _ ->
                ivNotification.visibility = View.VISIBLE
            }
    }

    override fun onResume() {
        super.onResume()
        setActiveNav(navHome)
    }

    private fun setActiveNav(selected: ImageView) {
        val navItems = listOf(navHome, navSearch, ivNotification, navProfile)

        for (item in navItems) {
            item.isSelected = false
        }
        selected.isSelected = true
    }
}
