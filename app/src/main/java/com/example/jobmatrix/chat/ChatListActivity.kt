package com.example.jobmatrix.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jobmatrix.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatListActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: ChatListAdapter
    private val items = mutableListOf<ChatPreviewItem>()
    private var chatsListener: ListenerRegistration? = null
    private lateinit var emptyState: View
    private lateinit var rvShimmer: RecyclerView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView

    private val avatarColors = listOf(
        R.color.avatar_1, R.color.avatar_2, R.color.avatar_3,
        R.color.avatar_5, R.color.avatar_6, R.color.avatar_7,
        R.color.avatar_8, R.color.avatar_9, R.color.avatar_10
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        emptyState = findViewById(R.id.emptyState)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            loadChats(auth.currentUser?.uid ?: return@setOnRefreshListener)
        }
        swipeRefresh.setColorSchemeResources(R.color.color_accent)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.color_surface)
        rvShimmer = findViewById(R.id.rvShimmer)
        rvShimmer.layoutManager = LinearLayoutManager(this)
        rvShimmer.adapter = com.example.jobmatrix.student.ShimmerAdapter()
        findViewById<android.widget.ImageView>(R.id.ivBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.rvChats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatListAdapter(items) { item ->
            startActivity(
                Intent(this, ChatActivity::class.java)
                    .putExtra("applicationId", item.applicationId)
            )
        }
        recyclerView.adapter = adapter

        listenChats()
    }

    private fun listenChats() {
        val myUid = auth.currentUser?.uid ?: return

        // Query chats where user is student or employer
        chatsListener = db.collection("chats")
            .whereEqualTo("studentId", myUid)
            .addSnapshotListener { _, _ -> } // triggers rebuild below

        // Use a combined approach: fetch both sides
        loadChats(myUid)
    }

    private fun loadChats(myUid: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val studentChats = db.collection("chats")
                    .whereEqualTo("studentId", myUid).get().await()
                val employerChats = db.collection("chats")
                    .whereEqualTo("employerId", myUid).get().await()

                val allDocs = (studentChats.documents + employerChats.documents)
                    .distinctBy { it.id }

                // Fetch unread counts
                val unreadSnap = db.collection("notifications")
                    .whereEqualTo("recipientId", myUid)
                    .whereEqualTo("isRead", false)
                    .whereEqualTo("type", "Message")
                    .get().await()

                val unreadByApp = unreadSnap.documents
                    .groupBy { it.getString("applicationId") ?: "" }
                    .mapValues { it.value.size }

                val result = mutableListOf<ChatPreviewItem>()

                for (doc in allDocs) {
                    val studentId = doc.getString("studentId") ?: ""
                    val employerId = doc.getString("employerId") ?: ""
                    val jobTitle = doc.getString("jobTitle") ?: ""
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val lastMessageAt = doc.getLong("lastMessageAt") ?: 0L
                    val otherUid = if (myUid == studentId) employerId else studentId

                    if (otherUid.isBlank()) continue

                    // Fetch other person's name + online status
                    val userDoc = db.collection("users").document(otherUid).get().await()
                    val name = userDoc.getString("name") ?: "Unknown"
                    val isOnline = userDoc.getBoolean("isOnline") ?: false

                    val initial = name.firstOrNull()?.uppercase() ?: "?"
                    val color = androidx.core.content.ContextCompat.getColor(
                        this@ChatListActivity,
                        avatarColors[otherUid.hashCode().and(0x7fffffff) % avatarColors.size]
                    )

                    result.add(
                        ChatPreviewItem(
                            applicationId = doc.id,
                            otherPersonName = name,
                            jobTitle = jobTitle,
                            lastMessage = lastMessage,
                            lastMessageAt = lastMessageAt,
                            unreadCount = unreadByApp[doc.id] ?: 0,
                            isOnline = isOnline,
                            avatarInitial = initial,
                            avatarColor = color
                        )
                    )
                }

                result.sortByDescending { it.lastMessageAt }

                rvShimmer.visibility = View.GONE
                recyclerView.alpha = 0f
                adapter.updateItems(result)
                recyclerView.animate().alpha(1f).setDuration(250).start()
                emptyState.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
                swipeRefresh.isRefreshing = false

            } catch (e: Exception) {
                android.util.Log.e("JM_CHATLIST", "Load failed", e)
                rvShimmer.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats(auth.currentUser?.uid ?: return)
    }

    override fun onDestroy() {
        super.onDestroy()
        chatsListener?.remove()
    }
}