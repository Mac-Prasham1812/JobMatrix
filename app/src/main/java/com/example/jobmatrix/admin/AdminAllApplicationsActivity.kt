package com.example.jobmatrix.admin

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminAllApplicationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    // Chips
    private lateinit var chipAll: TextView
    private lateinit var chipApplied: TextView
    private lateinit var chipShortlisted: TextView
    private lateinit var chipRejected: TextView

    private val db = FirebaseFirestore.getInstance()
    private val appList = ArrayList<ApplicationAdminModel>()
    private val filteredList = ArrayList<ApplicationAdminModel>()
    private lateinit var adapter: AdminApplicationAdapter

    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_all_applications)

        recyclerView = findViewById(R.id.recyclerApplications)
        progressBar = findViewById(R.id.progressApplications)
        tvEmpty = findViewById(R.id.tvEmptyApplications)

        chipAll = findViewById(R.id.chipAll)
        chipApplied = findViewById(R.id.chipApplied)
        chipShortlisted = findViewById(R.id.chipShortlisted)
        chipRejected = findViewById(R.id.chipRejected)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminApplicationAdapter(filteredList)
        recyclerView.adapter = adapter

        setActiveChip(chipAll)

        chipAll.setOnClickListener {
            currentFilter = "All"
            setActiveChip(chipAll)
            applyFilter()
        }

        chipApplied.setOnClickListener {
            currentFilter = "Applied"
            setActiveChip(chipApplied)
            applyFilter()
        }

        chipShortlisted.setOnClickListener {
            currentFilter = "Shortlisted"
            setActiveChip(chipShortlisted)
            applyFilter()
        }

        chipRejected.setOnClickListener {
            currentFilter = "Rejected"
            setActiveChip(chipRejected)
            applyFilter()
        }

        loadAllApplications()
    }

    private fun loadAllApplications() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        tvEmpty.visibility = View.GONE

        db.collection("applications")
            .get()
            .addOnSuccessListener { snapshot ->
                appList.clear()

                for (doc in snapshot.documents) {
                    val app = ApplicationAdminModel(
                        applicationId = doc.id,
                        studentId = doc.getString("studentId") ?: "",
                        jobId = doc.getString("jobId") ?: "",
                        jobTitle = doc.getString("jobTitle") ?: "N/A",
                        companyName = doc.getString("companyName") ?: "N/A",
                        resumeLink = doc.getString("resumeLink") ?: "",
                        status = doc.getString("status") ?: "Applied"
                    )
                    appList.add(app)
                }

                progressBar.visibility = View.GONE
                applyFilter()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load applications", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter() {
        filteredList.clear()

        if (currentFilter == "All") {
            filteredList.addAll(appList)
        } else {
            for (app in appList) {
                if (app.status.equals(currentFilter, true)) {
                    filteredList.add(app)
                }
            }
        }

        adapter.notifyDataSetChanged()

        if (filteredList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setActiveChip(selectedChip: TextView) {
        val chips = listOf(chipAll, chipApplied, chipShortlisted, chipRejected)

        for (chip in chips) {
            chip.setBackgroundResource(R.drawable.bg_chip_admin)
            chip.setTextColor(resources.getColor(android.R.color.black))
        }

        selectedChip.setBackgroundResource(R.drawable.bg_chip_admin_active)
        selectedChip.setTextColor(resources.getColor(android.R.color.white))
    }
}
