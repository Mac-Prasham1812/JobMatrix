package com.example.jobmatrix.student

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.NotificationModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R

class NotificationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationTabs: TabLayout
    private lateinit var adapter: NotificationAdapter

    private val allNotifications = mutableListOf<NotificationModel>()
    private val visibleNotifications = mutableListOf<NotificationModel>()

    private val db = FirebaseFirestore.getInstance()

    private val tabLabels = listOf("All", "Message", "Shortlisted", "Rejected")
    private var selectedFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        recyclerView = findViewById(R.id.rvNotifications)
        notificationTabs = findViewById(R.id.notificationTabs)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NotificationAdapter(visibleNotifications)
        recyclerView.adapter = adapter

        setupTabs()
        loadNotifications()
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupTabs() {
        notificationTabs.removeAllTabs()

        for (label in tabLabels) {
            val tab = notificationTabs.newTab()
            val tabView = LayoutInflater.from(this)
                .inflate(R.layout.item_tab_notification, null)

            tabView.findViewById<TextView>(R.id.tvTabTitle).text = label
            tab.customView = tabView
            notificationTabs.addTab(tab)
        }

        notificationTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: Tab) {
                val title = tab.customView?.findViewById<TextView>(R.id.tvTabTitle)?.text
                selectedFilter = title?.toString() ?: "All"
                filterNotifications()
            }

            override fun onTabUnselected(tab: Tab) {}
            override fun onTabReselected(tab: Tab) {}
        })
    }

    private fun loadNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("studentId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                allNotifications.clear()

                for (document in snapshot.documents) {
                    val notification = document.toObject(NotificationModel::class.java)
                    if (notification != null) {
                        allNotifications.add(notification.copy(notificationId = document.id))
                    }
                }

                updateTabCounts()
                filterNotifications()
            }
    }

    private fun updateTabCounts() {
        for (i in 0 until notificationTabs.tabCount) {
            val tab = notificationTabs.getTabAt(i) ?: continue
            val tabView = tab.customView ?: continue
            val label = tabView.findViewById<TextView>(R.id.tvTabTitle).text.toString()
            val countView = tabView.findViewById<TextView>(R.id.tvTabCount)

            val count = if (label == "All") {
                allNotifications.size
            } else {
                allNotifications.count { it.type.equals(label, ignoreCase = true) }
            }

            countView.text = count.toString()
        }
    }

    private fun filterNotifications() {
        visibleNotifications.clear()

        if (selectedFilter == "All") {
            visibleNotifications.addAll(allNotifications)
        } else {
            visibleNotifications.addAll(
                allNotifications.filter { it.type.equals(selectedFilter, ignoreCase = true) }
            )
        }

        adapter.resetAnimation()
        adapter.notifyDataSetChanged()
    }
}