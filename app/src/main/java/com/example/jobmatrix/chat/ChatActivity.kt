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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var applicationId: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var etMessage: EditText

    private val messages = mutableListOf<ChatMessage>()
    private val listItems = mutableListOf<ChatListItem>()

    private var studentId = ""
    private var employerId = ""

    private var chatReady = false

    private var editingMessageId: String? = null
    private lateinit var editBar: android.widget.LinearLayout
    private lateinit var emptyStateContainer: android.widget.LinearLayout
    private var messagesLoadedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        applicationId = intent.getStringExtra("applicationId") ?: ""
        if (applicationId.isEmpty()) { finish(); return }

        recyclerView = findViewById(R.id.rvMessages)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        etMessage = findViewById(R.id.etMessage)

        editBar = findViewById(R.id.editBar)
        findViewById<ImageView>(R.id.btnCancelEdit).setOnClickListener { cancelEdit() }
        recyclerView.itemAnimator?.changeDuration = 250

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(listItems, auth.currentUser?.uid ?: "") { message ->
            showEditDeleteDialog(message)
        }
        recyclerView.adapter = adapter
        recyclerView.itemAnimator?.changeDuration = 250

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

                        ensureChatDoc(companyName, jobTitle) {
                            loadMessages()
                        }
                    }
            }
    }

//    private fun ensureChatDoc(companyName: String, jobTitle: String) {
//        val chatData = hashMapOf(
//            "studentId" to studentId,
//            "employerId" to employerId,
//            "jobTitle" to jobTitle,
//            "companyName" to companyName
//        )
//        db.collection("chats").document(applicationId).set(chatData, com.google.firebase.firestore.SetOptions.merge())
//    }

//    private fun loadMessages() {
//        db.collection("chats").document(applicationId)
//            .collection("messages")
//            .orderBy("timestamp", Query.Direction.ASCENDING)
//            .addSnapshotListener { snapshot, error ->
//                if (error != null || snapshot == null) return@addSnapshotListener
//
//                messages.clear()
//                for (doc in snapshot.documents) {
//                    doc.toObject(ChatMessage::class.java)?.let {
//                        val fixedIsRead = doc.getBoolean("isRead") ?: false
//                        val fixedIsDeleted = doc.getBoolean("isDeleted") ?: false
//                        messages.add(it.copy(messageId = doc.id, isRead = fixedIsRead, isDeleted = fixedIsDeleted))
//                    }
//                }
//
//                val myUid = auth.currentUser?.uid ?: ""
//                for (doc in snapshot.documents) {
//                    val senderId = doc.getString("senderId") ?: ""
//                    val isRead = doc.getBoolean("isRead") ?: false
//                    if (senderId != myUid && !isRead) {
//                        doc.reference.update("isRead", true)
//                    }
//                }
//
//                buildListItems()
//                adapter.notifyDataSetChanged()
//                emptyStateContainer.visibility = if (messages.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
//                if (listItems.isNotEmpty()) recyclerView.scrollToPosition(listItems.size - 1)
//            }
//    }


    private fun loadMessages() {
        db.collection("chats").document(applicationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) return@addSnapshotListener

                val layoutManager =
                    recyclerView.layoutManager as LinearLayoutManager

                val wasAtBottom =
                    !messagesLoadedOnce ||
                            layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2
                messages.clear()

                for (doc in snapshot.documents) {
                    doc.toObject(ChatMessage::class.java)?.let {
                        val fixedIsRead = doc.getBoolean("isRead") ?: false
                        val fixedIsDeleted = doc.getBoolean("isDeleted") ?: false

                        messages.add(
                            it.copy(
                                messageId = doc.id,
                                isRead = fixedIsRead,
                                isDeleted = fixedIsDeleted
                            )
                        )
                    }
                }

                val myUid = auth.currentUser?.uid ?: ""

                for (doc in snapshot.documents) {
                    val senderId = doc.getString("senderId") ?: ""
                    val isRead = doc.getBoolean("isRead") ?: false

                    if (senderId != myUid && !isRead) {
                        doc.reference.update("isRead", true)
                    }
                }

                buildListItems()
                adapter.notifyDataSetChanged()

                emptyStateContainer.visibility =
                    if (messages.isEmpty()) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }

                if (wasAtBottom) {
                    recyclerView.post {
                        if (listItems.isNotEmpty()) {
                            recyclerView.scrollToPosition(listItems.size - 1)
                        }
                    }
                }

                messagesLoadedOnce = true
            }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        if (editingMessageId != null) {
            db.collection("chats").document(applicationId)
                .collection("messages").document(editingMessageId!!)
                .update(mapOf("text" to text, "edited" to true))
            cancelEdit()
            return
        }

        if (!chatReady) {
            android.widget.Toast.makeText(this, "Please wait...", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val myUid = auth.currentUser?.uid ?: return
        val role = if (myUid == studentId) "Student" else "Employer"
        android.util.Log.d("JM_CHAT", "sendMessage role=$role myUid=$myUid studentId=$studentId")

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

        if (role == "Employer") {
            android.util.Log.d("JM_CHAT", "calling createStudentNotification")
            createStudentNotification(text)
        } else {
            createEmployerNotification(text)
        }

        etMessage.setText("")
    }

    private fun createStudentNotification(text: String) {
        db.collection("applications").document(applicationId).get()
            .addOnSuccessListener { appDoc ->
                val jobTitle = appDoc.getString("jobTitle") ?: ""
                val companyName = appDoc.getString("companyName") ?: ""

                db.collection("notifications")
                    .whereEqualTo("applicationId", applicationId)
                    .whereEqualTo("employerId", employerId)
                    .whereEqualTo("type", "Message")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val docId = snapshot.documents[0].id
                            db.collection("notifications").document(docId)
                                .update(mapOf(
                                    "message" to text,
                                    "createdAt" to System.currentTimeMillis(),
                                    "isRead" to false
                                ))
                        } else {
                            val notif = hashMapOf(
                                "studentId" to studentId,
                                "employerId" to employerId,
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
                    .addOnFailureListener { e ->
                        android.util.Log.e("JM_CHAT", "notif query failed", e)
                    }

                db.collection("users").document(studentId).get()
                    .addOnSuccessListener { userDoc ->
                        val token = userDoc.getString("fcmToken") ?: ""
                        if (token.isNotBlank()) {
                            lifecycleScope.launch {
                                try {
                                    com.example.jobmatrix.network.RetrofitClient.api.sendNotification(
                                        com.example.jobmatrix.network.NotifyRequest(token, "New message from ${companyName.ifBlank { "Employer" }}", text)
                                    )
                                } catch (e: Exception) { }
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("JM_CHAT", "appDoc fetch failed", e)
            }
    }

    private fun createEmployerNotification(text: String) {
        db.collection("applications").document(applicationId).get()
            .addOnSuccessListener { appDoc ->
                val jobTitle = appDoc.getString("jobTitle") ?: ""
                val companyName = appDoc.getString("companyName") ?: ""

                db.collection("notifications")
                    .whereEqualTo("applicationId", applicationId)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("type", "Message")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val docId = snapshot.documents[0].id
                            db.collection("notifications").document(docId)
                                .update(mapOf(
                                    "message" to text,
                                    "createdAt" to System.currentTimeMillis(),
                                    "isRead" to false
                                ))
                        } else {
                            val notif = hashMapOf(
                                "employerId" to employerId,
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
                    .addOnFailureListener { e ->
                        android.util.Log.e("JM_CHAT", "employer notif query failed", e)
                    }
            }
    }

    private fun ensureChatDoc(companyName: String, jobTitle: String, onDone: () -> Unit) {
        val chatData = hashMapOf(
            "studentId" to studentId,
            "employerId" to employerId,
            "jobTitle" to jobTitle,
            "companyName" to companyName
        )
        db.collection("chats").document(applicationId)
            .set(chatData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                chatReady = true
                onDone()
            }
    }

    private fun showEditDeleteDialog(message: com.example.jobmatrix.model.ChatMessage) {
        val view = layoutInflater.inflate(R.layout.dialog_chat_action_menu, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.btnEditMsg).setOnClickListener {
            dialog.dismiss()
            startEdit(message)
        }
        view.findViewById<TextView>(R.id.btnDeleteMsg).setOnClickListener {
            dialog.dismiss()
            confirmDelete(message)
        }
        dialog.show()
    }

    private fun startEdit(message: com.example.jobmatrix.model.ChatMessage) {
        editingMessageId = message.messageId
        etMessage.setText(message.text)
        etMessage.setSelection(etMessage.text.length)
        editBar.visibility = android.view.View.VISIBLE
    }

    private fun cancelEdit() {
        editingMessageId = null
        etMessage.setText("")
        editBar.visibility = android.view.View.GONE
    }

    private fun confirmDelete(message: com.example.jobmatrix.model.ChatMessage) {
        val view = layoutInflater.inflate(R.layout.dialog_delete_message, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            db.collection("chats").document(applicationId)
                .collection("messages").document(message.messageId)
                .update(mapOf("text" to "", "isDeleted" to true))
        }
        dialog.show()
    }

    private fun dateLabelFor(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = java.util.Calendar.getInstance()
        val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }

        return when {
            cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) -> "Yesterday"
            else -> android.text.format.DateFormat.format("dd MMM yyyy", timestamp).toString()
        }
    }

    private fun buildListItems() {
        listItems.clear()
        var lastLabel = ""
        for (msg in messages) {
            val label = dateLabelFor(msg.timestamp)
            if (label != lastLabel) {
                listItems.add(ChatListItem.Header(label))
                lastLabel = label
            }
            listItems.add(ChatListItem.MessageItem(msg))
        }
    }
}