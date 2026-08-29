package com.example.jobmatrix.student

import android.os.Bundle
import android.text.format.DateUtils
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
import android.widget.Toast

class NotificationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var notificationTabs: TabLayout
    private lateinit var adapter: NotificationAdapter

    private val allNotifications = mutableListOf<NotificationModel>()
    private val visibleNotifications = mutableListOf<NotificationListItem>()

    private val db = FirebaseFirestore.getInstance()

    private val tabLabels = listOf("All", "Message", "Shortlisted", "Rejected")
    private var selectedFilter = "All"
    private lateinit var emptyState: android.view.View
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        recyclerView = findViewById(R.id.rvNotifications)
        emptyState = findViewById(R.id.emptyState)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.color_accent)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.color_surface)
        swipeRefresh.setOnRefreshListener {
            loadNotifications()
            syncBadgeCount()
        }

        notificationTabs = findViewById(R.id.notificationTabs)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = NotificationAdapter(visibleNotifications)
        recyclerView.adapter = adapter


        findViewById<TextView>(R.id.btnMarkAllRead).setOnClickListener { markAllAsRead() }

        val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0, androidx.recyclerview.widget.ItemTouchHelper.LEFT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = visibleNotifications[position]

                    if (item is NotificationListItem.Item) {
                        val notification = item.notification

                        db.collection("notifications")
                            .document(notification.notificationId)
                            .delete()
                            .addOnSuccessListener {
                                visibleNotifications.removeAt(position)
                                adapter.notifyItemRemoved(position)
                                syncBadgeCount()

                                showToast("Notification deleted")
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e(
                                    "JM_NOTIFICATION",
                                    "Notification delete failed",
                                    e
                                )
                                adapter.notifyItemChanged(position)
                            }
                    } else {
                        adapter.notifyItemChanged(position)
                    }
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)

        setupTabs()
        loadNotifications()
        syncBadgeCount()
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
            .whereEqualTo("recipientId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e(
                        "JM_NOTIFICATION",
                        "Notification query failed",
                        error
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                allNotifications.clear()

                for (document in snapshot.documents) {
                    val notification = document.toObject(NotificationModel::class.java)
                    if (notification != null && !notification.type.equals("Applied", ignoreCase = true)) {
                        val fixedIsRead = document.getBoolean("isRead") ?: false
                        allNotifications.add(notification.copy(notificationId = document.id, isRead = fixedIsRead))
                    }
                }

                updateTabCounts()
                filterNotifications()
            }
    }

    private fun updateTabCounts() {
        val btnMarkAllRead = findViewById<TextView>(R.id.btnMarkAllRead)
        btnMarkAllRead.visibility = if (allNotifications.any { !it.isRead })
            android.view.View.VISIBLE else android.view.View.GONE
        for (i in 0 until notificationTabs.tabCount) {
            val tab = notificationTabs.getTabAt(i) ?: continue
            val tabView = tab.customView ?: continue
            val label = tabView.findViewById<TextView>(R.id.tvTabTitle).text.toString()
            val countView = tabView.findViewById<TextView>(R.id.tvTabCount)

            val count = if (label == "All") {
                allNotifications.count { !it.isRead }
            } else {
                allNotifications.count { it.type.equals(label, ignoreCase = true) && !it.isRead }
            }

            countView.text = if (count > 0) count.toString() else ""
            countView.visibility = if (count > 0) android.view.View.VISIBLE else android.view.View.GONE
        }
    }
    private fun filterNotifications() {
        val filtered = if (selectedFilter == "All") {
            allNotifications
        } else {
            allNotifications.filter { it.type.equals(selectedFilter, ignoreCase = true) }
        }

        visibleNotifications.clear()

        val today = filtered.filter { getBucket(it.createdAt) == "Today" }
        val yesterday = filtered.filter { getBucket(it.createdAt) == "Yesterday" }
        val thisWeek = filtered.filter { getBucket(it.createdAt) == "This Week" }
        val earlier = filtered.filter { getBucket(it.createdAt) == "Earlier" }

        listOf("Today" to today, "Yesterday" to yesterday, "This Week" to thisWeek, "Earlier" to earlier)
            .forEach { (label, items) ->
                if (items.isNotEmpty()) {
                    visibleNotifications.add(NotificationListItem.Header(label))
                    items.forEach { visibleNotifications.add(NotificationListItem.Item(it)) }
                }
            }

        recyclerView.alpha = 0f
        adapter.resetAnimation()
        adapter.notifyDataSetChanged()
        recyclerView.animate().alpha(1f).setDuration(250).start()
        swipeRefresh.isRefreshing = false
        emptyState.visibility = if (visibleNotifications.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        recyclerView.visibility = if (visibleNotifications.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun getBucket(timestamp: Long): String {
        if (timestamp <= 0L) return "Earlier"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val oneDay = 24 * 60 * 60 * 1000L

        return when {
            DateUtils.isToday(timestamp) -> "Today"
            DateUtils.isToday(timestamp + oneDay) -> "Yesterday"
            diff < 7 * oneDay -> "This Week"
            else -> "Earlier"
        }
    }


    private fun markAllAsRead() {
        val unread = allNotifications.filter { !it.isRead }
        if (unread.isEmpty()) return

        val batch = db.batch()
        unread.forEach { n ->
            batch.update(db.collection("notifications").document(n.notificationId), "isRead", true)
        }
        batch.commit()
            .addOnSuccessListener { syncBadgeCount() }
    }

    private fun syncBadgeCount() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                if (count > 0) {
                    me.leolin.shortcutbadger.ShortcutBadger.applyCount(applicationContext, count)
                } else {
                    me.leolin.shortcutbadger.ShortcutBadger.removeCount(applicationContext)
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e(
                    "JM_NOTIFICATION",
                    "Badge count query failed",
                    e
                )
            }
    }

    private fun showToast(message: String) {
        val layout = layoutInflater.inflate(
            R.layout.toast_custom,
            null
        )

        layout.findViewById<TextView>(R.id.tvToastMessage).text = message

        Toast(this).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
            show()
        }
    }
}