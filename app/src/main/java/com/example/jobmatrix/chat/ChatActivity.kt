package com.example.jobmatrix.chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ChatMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R

class ChatActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var applicationId: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var etMessage: EditText

    private val messages = mutableListOf<ChatMessage>()

    private var studentId = ""
    private var employerId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        applicationId = intent.getStringExtra("applicationId") ?: ""
        if (applicationId.isEmpty()) { finish(); return }

        recyclerView = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(messages, auth.currentUser?.uid ?: "")
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnSend).setOnClickListener { sendMessage() }

        resolveParticipants()
    }

    private fun resolveParticipants() {
        db.collection("applications").document(applicationId).get()
            .addOnSuccessListener { appDoc ->
                studentId = appDoc.getString("studentId") ?: ""
                val jobId = appDoc.getString("jobId") ?: ""
                val jobTitle = appDoc.getString("jobTitle") ?: ""
                val companyName = appDoc.getString("companyName") ?: ""

                findViewById<TextView>(R.id.tvChatSubtitle).text = jobTitle

                db.collection("jobs").document(jobId).get()
                    .addOnSuccessListener { jobDoc ->
                        employerId = jobDoc.getString("employerId") ?: ""

                        val myUid = auth.currentUser?.uid ?: ""
                        if (myUid == studentId) {
                            findViewById<TextView>(R.id.tvChatTitle).text = companyName
                        } else {
                            db.collection("users").document(studentId).get()
                                .addOnSuccessListener { studentDoc ->
                                    findViewById<TextView>(R.id.tvChatTitle).text =
                                        studentDoc.getString("name") ?: "Student"
                                }
                        }

                        ensureChatDoc(companyName, jobTitle)
                        loadMessages()
                    }
            }
    }

    private fun ensureChatDoc(companyName: String, jobTitle: String) {
        val chatData = hashMapOf(
            "studentId" to studentId,
            "employerId" to employerId,
            "jobTitle" to jobTitle,
            "companyName" to companyName
        )
        db.collection("chats").document(applicationId).set(chatData, com.google.firebase.firestore.SetOptions.merge())
    }

    private fun loadMessages() {
        db.collection("chats").document(applicationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                messages.clear()
                for (doc in snapshot.documents) {
                    doc.toObject(ChatMessage::class.java)?.let {
                        messages.add(it.copy(messageId = doc.id))
                    }
                }
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
            }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        val myUid = auth.currentUser?.uid ?: return
        val role = if (myUid == studentId) "Student" else "Employer"

        val messageData = hashMapOf(
            "senderId" to myUid,
            "senderRole" to role,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("chats").document(applicationId)
            .collection("messages")
            .add(messageData)

        db.collection("chats").document(applicationId)
            .update(mapOf("lastMessage" to text, "lastMessageAt" to System.currentTimeMillis()))

        // If employer sends, also create a "Message" notification for the student.
        if (role == "Employer") {
            createStudentNotification(text)
        }

        etMessage.setText("")
    }

    private fun createStudentNotification(text: String) {
        db.collection("applications").document(applicationId).get()
            .addOnSuccessListener { appDoc ->
                val jobTitle = appDoc.getString("jobTitle") ?: ""
                val companyName = appDoc.getString("companyName") ?: ""

                val notif = hashMapOf(
                    "studentId" to studentId,
                    "applicationId" to applicationId,
                    "jobTitle" to jobTitle,
                    "companyName" to companyName,
                    "message" to text,
                    "type" to "Message",
                    "createdAt" to System.currentTimeMillis(),
                    "isRead" to false
                )
                db.collection("notifications").add(notif)
            }
    }
}