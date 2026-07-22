package com.example.jobmatrix.student

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.NotificationModel
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R
import com.google.android.material.tabs.TabLayout.Tab

class NotificationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationTabs: TabLayout
    private lateinit var adapter: NotificationAdapter

    private val allNotifications =
        mutableListOf<NotificationModel>()

    private val visibleNotifications =
        mutableListOf<NotificationModel>()

    private val db =
        FirebaseFirestore.getInstance()

    private var selectedFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_notification)

        recyclerView = findViewById(R.id.rvNotifications)
        notificationTabs = findViewById(R.id.notificationTabs)

        recyclerView.layoutManager =
            LinearLayoutManager(this)

        adapter = NotificationAdapter(
            visibleNotifications
        )

        recyclerView.adapter = adapter

        notificationTabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectedFilter =
                        tab.text?.toString() ?: "All"

                    filterNotifications()
                }

                override fun onTabUnselected(tab: Tab) {
                    // No action required
                }

                override fun onTabReselected(tab: Tab) {
                    // No action required
                }
            }
        )

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId =
            FirebaseAuth.getInstance()
                .currentUser
                ?.uid
                ?: return

        db.collection("notifications")
            .whereEqualTo("studentId", userId)
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }

                allNotifications.clear()

                for (document in snapshot.documents) {
                    val notification =
                        document.toObject(
                            NotificationModel::class.java
                        )

                    if (notification != null) {
                        allNotifications.add(
                            notification.copy(
                                notificationId = document.id
                            )
                        )
                    }
                }

                filterNotifications()
            }
    }

    private fun filterNotifications() {
        visibleNotifications.clear()

        if (selectedFilter == "All") {
            visibleNotifications.addAll(
                allNotifications
            )
        } else {
            visibleNotifications.addAll(
                allNotifications.filter { notification ->
                    notification.type.equals(
                        selectedFilter,
                        ignoreCase = true
                    )
                }
            )
        }

        adapter.notifyDataSetChanged()
    }
}