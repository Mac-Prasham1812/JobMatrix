package com.example.jobmatrix.admin

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.profile.ProfileActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminDashboardActivity : AppCompatActivity() {

    // TextViews
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvTotalEmployers: TextView
    private lateinit var tvTotalJobs: TextView
    private lateinit var tvTotalApplications: TextView

    // Progress Bars
    private lateinit var progressStudents: ProgressBar
    private lateinit var progressEmployers: ProgressBar
    private lateinit var progressJobsMini: ProgressBar
    private lateinit var progressApplications: ProgressBar

    // Action buttons
    private lateinit var btnManageStudents: LinearLayout
    private lateinit var btnManageEmployers: LinearLayout
    private lateinit var btnManageJobs: LinearLayout
    private lateinit var btnViewApplications: LinearLayout

    private lateinit var ivAdminProfile: ImageView

    // Firebase
    private val db = FirebaseFirestore.getInstance()

    // Store totals (important for dynamic bars)
    private var totalStudents = 0
    private var totalEmployers = 0
    private var totalJobs = 0
    private var totalApplications = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        initViews()
        loadDashboardCounts()
        setupClicks()
        setupProfileClick()
    }

    private fun initViews() {
        tvTotalStudents = findViewById(R.id.tvTotalStudents)
        tvTotalEmployers = findViewById(R.id.tvTotalEmployers)
        tvTotalJobs = findViewById(R.id.tvTotalJobs)
        tvTotalApplications = findViewById(R.id.tvTotalApplications)

        progressStudents = findViewById(R.id.progressStudents)
        progressEmployers = findViewById(R.id.progressEmployers)
        progressJobsMini = findViewById(R.id.progressJobsMini)
        progressApplications = findViewById(R.id.progressApplications)

        btnManageStudents = findViewById(R.id.btnManageStudents)
        btnManageEmployers = findViewById(R.id.btnManageEmployers)
        btnManageJobs = findViewById(R.id.btnManageJobs)
        btnViewApplications = findViewById(R.id.btnViewApplications)

        ivAdminProfile = findViewById(R.id.ivAdminProfile)
    }

    private fun setupProfileClick() {
        ivAdminProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun loadDashboardCounts() {

        // Students
        db.collection("users")
            .whereEqualTo("role", "Student")
            .addSnapshotListener { snapshot, _ ->
                totalStudents = snapshot?.size() ?: 0
                tvTotalStudents.text = totalStudents.toString()
                updateAllBars()
            }

        // Employers
        db.collection("users")
            .whereEqualTo("role", "Employer")
            .addSnapshotListener { snapshot, _ ->
                totalEmployers = snapshot?.size() ?: 0
                tvTotalEmployers.text = totalEmployers.toString()
                updateAllBars()
            }

        // Jobs
        db.collection("jobs")
            .addSnapshotListener { snapshot, _ ->
                totalJobs = snapshot?.size() ?: 0
                tvTotalJobs.text = totalJobs.toString()
                updateAllBars()
            }

        // Applications
        db.collection("applications")
            .addSnapshotListener { snapshot, _ ->
                totalApplications = snapshot?.size() ?: 0
                tvTotalApplications.text = totalApplications.toString()
                updateAllBars()
            }
    }

    /**
     * This makes all progress bars dynamic.
     * The largest value gets a full bar.
     * Others scale automatically.
     */
    private fun updateAllBars() {

        val maxValue = listOf(
            totalStudents,
            totalEmployers,
            totalJobs,
            totalApplications
        ).maxOrNull() ?: 1

        val safeMax = if (maxValue == 0) 1 else maxValue

        // Add headroom (20% extra space)
        val visualMax = (safeMax * 1.2).toInt().coerceAtLeast(safeMax + 1)

        progressStudents.max = visualMax
        progressEmployers.max = visualMax
        progressJobsMini.max = visualMax
        progressApplications.max = visualMax

        progressStudents.progress = totalStudents
        progressEmployers.progress = totalEmployers
        progressJobsMini.progress = totalJobs
        progressApplications.progress = totalApplications
    }


    private fun setupClicks() {

        btnManageStudents.setOnClickListener {
            startActivity(Intent(this, AdminStudentsActivity::class.java))
        }

        btnManageEmployers.setOnClickListener {
            startActivity(Intent(this, AdminEmployersActivity::class.java))
        }

        btnManageJobs.setOnClickListener {
            startActivity(Intent(this, AdminJobsActivity::class.java))
        }

        btnViewApplications.setOnClickListener {
            startActivity(Intent(this, AdminAllApplicationsActivity::class.java))
        }
    }
}
