package com.example.jobmatrix.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R

class MyApplicationsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val allApplications = mutableListOf<ApplicationModel>()
    private lateinit var adapter: ApplicationAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tabs: TabLayout

    private val tabLabels = listOf("All", "Applied", "In Review", "Shortlisted", "Rejected")
    private var selectedFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_applications)

        recyclerView = findViewById(R.id.rvApplications)
        emptyState = findViewById(R.id.emptyState)
        tabs = findViewById(R.id.applicationTabs)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ApplicationAdapter(emptyList())
        recyclerView.adapter = adapter

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupTabs()
        loadApplications()
    }

    private fun setupTabs() {
        for (label in tabLabels) {
            val tab = tabs.newTab()
            val tabView = LayoutInflater.from(this).inflate(R.layout.item_tab_notification, null)
            tabView.findViewById<TextView>(R.id.tvTabTitle).text = label
            tabView.findViewById<View>(R.id.tvTabCount).visibility = View.GONE
            tab.customView = tabView
            tabs.addTab(tab)
        }

        val preselect = intent.getStringExtra("statusFilter")
        if (preselect != null) {
            val index = tabLabels.indexOf(preselect)
            if (index >= 0) {
                tabs.getTabAt(index)?.select()
                selectedFilter = preselect
            }
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedFilter = tab.customView?.findViewById<TextView>(R.id.tvTabTitle)?.text.toString()
                filterAndShow()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadApplications() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("applications")
            .whereEqualTo("studentId", uid)
            .orderBy("appliedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                allApplications.clear()
                for (doc in snapshot.documents) {
                    doc.toObject(ApplicationModel::class.java)?.let {
                        allApplications.add(it.copy(applicationId = doc.id))
                    }
                }
                filterAndShow()
            }
    }

    private fun filterAndShow() {
        val filtered = if (selectedFilter == "All") allApplications
        else allApplications.filter { it.status == selectedFilter }

        adapter.updateList(filtered)
        emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }
}