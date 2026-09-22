package com.example.jobmatrix.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R

class SupportChatActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_chat)

        findViewById<android.widget.ImageView>(R.id.ivBack).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.rvSupportMessages)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("recipientId", uid)
            .whereEqualTo("type", "AdminMessage")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val messages = snapshot.documents.map {
                    SupportMessage(
                        text = it.getString("message") ?: "",
                        createdAt = it.getLong("createdAt") ?: 0L
                    )
                }

                recyclerView.adapter = SupportMessageAdapter(messages)

                val unread = snapshot.documents.filter { it.getBoolean("isRead") != true }
                if (unread.isNotEmpty()) {
                    val batch = db.batch()
                    unread.forEach { batch.update(it.reference, "isRead", true) }
                    batch.commit()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("JM_SUPPORT", "Load failed", e)
            }
    }
}

data class SupportMessage(val text: String, val createdAt: Long)

class SupportMessageAdapter(
    private val items: List<SupportMessage>
) : RecyclerView.Adapter<SupportMessageAdapter.VH>() {

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvText: android.widget.TextView = view.findViewById(R.id.tvMessageText)
        val tvTime: android.widget.TextView = view.findViewById(R.id.tvMessageTime)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_received, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvText.text = item.text
        holder.tvTime.text = if (item.createdAt > 0L) {
            android.text.format.DateFormat.format("dd MMM, hh:mm a", item.createdAt)
        } else "Recently"
    }

    override fun getItemCount() = items.size
}