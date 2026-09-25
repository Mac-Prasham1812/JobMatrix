package com.example.jobmatrix.chat

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R

class SupportChatActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerView: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var emptyStateContainer: android.widget.LinearLayout
    private lateinit var adapter: SupportMessageAdapter

    private val messages = mutableListOf<SupportMessage>()
    private var messagesListener: ListenerRegistration? = null
    private var messagesLoadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_chat)

        val uid = auth.currentUser?.uid
        if (uid == null) { finish(); return }

        recyclerView = findViewById(R.id.rvSupportMessages)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        etMessage = findViewById(R.id.etSupportMessage)

        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SupportMessageAdapter(messages)
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnSupportSend).setOnClickListener { sendReply(uid) }

        loadMessages(uid)
    }

    private fun loadMessages(uid: String) {
        messagesListener = db.collection("notifications")
            .whereEqualTo("recipientId", uid)
            .whereEqualTo("type", "AdminMessage")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    android.util.Log.e("JM_SUPPORT", "Load failed", error)
                    return@addSnapshotListener
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val wasAtBottom = !messagesLoadedOnce ||
                        layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2

                messages.clear()
                for (doc in snapshot.documents) {
                    messages.add(
                        SupportMessage(
                            text = doc.getString("message") ?: "",
                            title = doc.getString("title") ?: "",
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            sender = doc.getString("sender") ?: "admin"
                        )
                    )
                }
                adapter.notifyDataSetChanged()

                emptyStateContainer.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE

                if (wasAtBottom) {
                    recyclerView.post {
                        if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
                    }
                }

                val unread = snapshot.documents.filter {
                    it.getString("sender") != "user" && it.getBoolean("isRead") != true
                }
                if (unread.isNotEmpty()) {
                    val batch = db.batch()
                    unread.forEach { batch.update(it.reference, "isRead", true) }
                    batch.commit()
                }

                messagesLoadedOnce = true
            }
    }

    private fun sendReply(uid: String) {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val data = hashMapOf(
            "recipientId" to uid,
            "type" to "AdminMessage",
            "sender" to "user",
            "fromUid" to uid,
            "message" to text,
            "createdAt" to System.currentTimeMillis(),
            "isRead" to true,
            "adminRead" to false
        )

        db.collection("notifications").add(data)
            .addOnSuccessListener {
                etMessage.setText("")
                db.collection("adminNotifications").add(
                    hashMapOf(
                        "type" to "SupportReply",
                        "message" to text,
                        "fromUid" to uid,
                        "createdAt" to System.currentTimeMillis(),
                        "isRead" to false
                    )
                )
            }
            .addOnFailureListener { e ->
                android.util.Log.e("JM_SUPPORT", "Send failed", e)
                android.widget.Toast.makeText(this, "Failed to send", android.widget.Toast.LENGTH_SHORT).show()
            }
    }

    override fun onStop() {
        super.onStop()
        messagesListener?.remove()
        messagesListener = null
    }
}

data class SupportMessage(
    val text: String,
    val title: String,
    val createdAt: Long,
    val sender: String
)