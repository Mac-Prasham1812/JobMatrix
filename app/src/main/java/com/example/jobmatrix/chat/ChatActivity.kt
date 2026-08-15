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
import com.google.firebase.firestore.ListenerRegistration

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
    private var messagesListener: ListenerRegistration? = null

    private var typingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var typingRunnable: Runnable? = null
    private var chatDocListener: ListenerRegistration? = null
    private var myRole = ""
    private var replyToMessage: ChatMessage? = null
    private lateinit var replyBar: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        applicationId = intent.getStringExtra("applicationId") ?: ""
        if (applicationId.isEmpty()) { finish(); return }

        recyclerView = findViewById(R.id.rvMessages)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        etMessage = findViewById(R.id.etMessage)

        etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!chatReady) return
                setTyping(true)
                typingRunnable?.let { typingHandler.removeCallbacks(it) }
                typingRunnable = Runnable { setTyping(false) }
                typingHandler.postDelayed(typingRunnable!!, 2000)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        editBar = findViewById(R.id.editBar)
        replyBar = findViewById(R.id.replyBar)
        findViewById<ImageView>(R.id.btnCancelReply).setOnClickListener { cancelReply() }
        findViewById<ImageView>(R.id.btnCancelEdit).setOnClickListener { cancelEdit() }
        recyclerView.itemAnimator?.changeDuration = 250

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(listItems, auth.currentUser?.uid ?: "",
            { message -> showEditDeleteDialog(message) },
            { replyId -> scrollToMessage(replyId) }
        )
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
                        myRole = if (myUid == studentId) "Student" else "Employer"
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

    private fun loadMessages() {
            messagesListener = db.collection("chats")
            .document(applicationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    android.util.Log.e("JM_CHAT", "Message load failed", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

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
            val messageId = editingMessageId!!

            db.collection("chats")
                .document(applicationId)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "text" to text,
                        "edited" to true
                    )
                )
                .addOnSuccessListener {
                    updateNotificationIfLatest(messageId, text)
                    cancelEdit()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e(
                        "JM_CHAT",
                        "Message edit failed",
                        e
                    )
                }

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
            "timestamp" to System.currentTimeMillis(),
            "replyToId" to (replyToMessage?.messageId ?: ""),
            "replyToText" to (replyToMessage?.text?.take(80) ?: ""),
            "replyToSender" to (replyToMessage?.let { if (it.senderId == myUid) "You" else findViewById<TextView>(R.id.tvChatTitle).text.toString() } ?: "")
        )

        db.collection("chats").document(applicationId)
            .collection("messages")
            .add(messageData)

        db.collection("chats").document(applicationId)
            .update(mapOf("lastMessage" to text, "lastMessageAt" to System.currentTimeMillis()))

        if (role == "Employer") {
            // Receiver preview + student push notification
            createStudentNotification(text)

            // Sender preview
            updateOwnNotificationPreview(
                recipientId = employerId,
                notificationId = "${applicationId}_employer_message",
                text = text
            )
        } else {
            // Receiver preview
            createEmployerNotification(text)

            // Sender preview
            updateOwnNotificationPreview(
                recipientId = studentId,
                notificationId = "${applicationId}_student_message",
                text = text
            )
        }

        etMessage.setText("")
        if (replyToMessage != null) cancelReply()
    }

    private fun createStudentNotification(text: String) {
        db.collection("applications")
            .document(applicationId)
            .get()
            .addOnSuccessListener { appDoc ->

                val jobTitle = appDoc.getString("jobTitle") ?: ""
                val companyName = appDoc.getString("companyName") ?: ""

                val notificationId = "${applicationId}_student_message"

                val notificationData = hashMapOf(
                    "studentId" to studentId,
                    "employerId" to employerId,
                    "recipientId" to studentId,
                    "applicationId" to applicationId,
                    "jobTitle" to jobTitle,
                    "companyName" to companyName,
                    "message" to text,
                    "type" to "Message",
                    "createdAt" to System.currentTimeMillis(),
                    "isRead" to false
                )

                db.collection("notifications")
                    .document(notificationId)
                    .set(
                        notificationData,
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        android.util.Log.d(
                            "JM_CHAT",
                            "Student notification created successfully"
                        )
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e(
                            "JM_CHAT",
                            "Student notification failed",
                            e
                        )
                    }

                // Send push notification to student
                db.collection("users")
                    .document(studentId)
                    .get()
                    .addOnSuccessListener { userDoc ->

                        val token = userDoc.getString("fcmToken") ?: ""

                        if (token.isNotBlank()) {
                            lifecycleScope.launch {
                                try {
                                    com.example.jobmatrix.network.RetrofitClient.api
                                        .sendNotification(
                                            com.example.jobmatrix.network.NotifyRequest(
                                                token,
                                                "New message from ${
                                                    companyName.ifBlank { "Employer" }
                                                }",
                                                text
                                            )
                                        )
                                } catch (e: Exception) {
                                    android.util.Log.e(
                                        "JM_CHAT",
                                        "Push notification failed",
                                        e
                                    )
                                }
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e(
                    "JM_CHAT",
                    "Application document fetch failed",
                    e
                )
            }
    }

    private fun createEmployerNotification(text: String) {
        db.collection("applications").document(applicationId).get()
            .addOnSuccessListener { appDoc ->

                val jobTitle = appDoc.getString("jobTitle") ?: ""
                val companyName = appDoc.getString("companyName") ?: ""

                val notificationId = "${applicationId}_employer_message"

                val notification = hashMapOf(
                    "employerId" to employerId,
                    "studentId" to studentId,
                    "recipientId" to employerId,
                    "applicationId" to applicationId,
                    "jobTitle" to jobTitle,
                    "companyName" to companyName,
                    "message" to text,
                    "type" to "Message",
                    "createdAt" to System.currentTimeMillis(),
                    "isRead" to false
                )

                db.collection("notifications")
                    .document(notificationId)
                    .set(
                        notification,
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        android.util.Log.d(
                            "JM_CHAT",
                            "Employer notification created: $notificationId"
                        )
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e(
                            "JM_CHAT",
                            "Employer notification failed",
                            e
                        )
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e(
                    "JM_CHAT",
                    "Application fetch failed",
                    e
                )
            }
    }

    private fun ensureChatDoc(
        companyName: String,
        jobTitle: String,
        onDone: () -> Unit
    ) {
        if (studentId.isBlank() || employerId.isBlank()) {
            android.util.Log.e(
                "JM_CHAT",
                "Cannot create chat: studentId or employerId is empty"
            )
            return
        }

        val chatData = hashMapOf(
            "studentId" to studentId,
            "employerId" to employerId,
            "jobTitle" to jobTitle,
            "companyName" to companyName
        )

        db.collection("chats")
            .document(applicationId)
            .set(
                chatData,
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener {
                android.util.Log.d(
                    "JM_CHAT",
                    "Chat document ready: $applicationId"
                )

                chatReady = true
                listenTyping()
                onDone()
            }
            .addOnFailureListener { e ->
                android.util.Log.e(
                    "JM_CHAT",
                    "Chat document write failed",
                    e
                )
            }
    }

    private fun showEditDeleteDialog(message: com.example.jobmatrix.model.ChatMessage) {
        val view = layoutInflater.inflate(R.layout.dialog_chat_action_menu, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val isOwn = message.senderId == (auth.currentUser?.uid ?: "")
        val btnEdit = view.findViewById<TextView>(R.id.btnEditMsg)
        val btnDelete = view.findViewById<TextView>(R.id.btnDeleteMsg)
        btnEdit.visibility = if (isOwn) android.view.View.VISIBLE else android.view.View.GONE
        btnDelete.visibility = if (isOwn) android.view.View.VISIBLE else android.view.View.GONE

        view.findViewById<TextView>(R.id.btnReplyMsg).setOnClickListener {
            dialog.dismiss()
            startReply(message)
        }
        btnEdit.setOnClickListener { dialog.dismiss(); startEdit(message) }
        btnDelete.setOnClickListener { dialog.dismiss(); confirmDelete(message) }
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
    override fun onStop() {
        super.onStop()
        setTyping(false)
        chatDocListener?.remove()
        chatDocListener = null

        messagesListener?.remove()
        messagesListener = null
    }

    private fun updateNotificationIfLatest(
        editedMessageId: String,
        newText: String
    ) {
        db.collection("chats")
            .document(applicationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->

                val latestMessage = snapshot.documents.firstOrNull()
                    ?: return@addOnSuccessListener

                // Do not update previews if an older message was edited
                if (latestMessage.id != editedMessageId) {
                    return@addOnSuccessListener
                }

                val batch = db.batch()

                val studentNotificationRef = db.collection("notifications")
                    .document("${applicationId}_student_message")

                val employerNotificationRef = db.collection("notifications")
                    .document("${applicationId}_employer_message")

                val previewData = mapOf(
                    "message" to newText
                )

                batch.set(
                    studentNotificationRef,
                    previewData,
                    com.google.firebase.firestore.SetOptions.merge()
                )

                batch.set(
                    employerNotificationRef,
                    previewData,
                    com.google.firebase.firestore.SetOptions.merge()
                )

                batch.update(
                    db.collection("chats").document(applicationId),
                    "lastMessage",
                    newText
                )

                batch.commit()
                    .addOnSuccessListener {
                        android.util.Log.d(
                            "JM_CHAT",
                            "Both latest notification previews updated"
                        )
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e(
                            "JM_CHAT",
                            "Notification preview update failed",
                            e
                        )
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e(
                    "JM_CHAT",
                    "Latest message check failed",
                    e
                )
            }
    }

    override fun onStart() {
        super.onStart()

        if (chatReady && messagesListener == null) {
            loadMessages()
        }
    }


    private fun updateOwnNotificationPreview(
        recipientId: String,
        notificationId: String,
        text: String
    ) {
        val data = hashMapOf(
            "recipientId" to recipientId,
            "applicationId" to applicationId,
            "message" to text,
            "type" to "Message",
            "createdAt" to System.currentTimeMillis(),
            "isRead" to true
        )

        db.collection("notifications")
            .document(notificationId)
            .set(
                data,
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnFailureListener { e ->
                android.util.Log.e(
                    "JM_CHAT",
                    "Own notification preview update failed",
                    e
                )
            }
    }

    private fun setTyping(isTyping: Boolean) {
        if (myRole.isEmpty() || applicationId.isEmpty()) return
        db.collection("chats").document(applicationId)
            .update("typing_${myRole.lowercase()}", isTyping)
    }

    private fun listenTyping() {
        chatDocListener = db.collection("chats").document(applicationId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) return@addSnapshotListener
                val otherRole = if (myRole == "Student") "employer" else "student"
                val isOtherTyping = doc.getBoolean("typing_$otherRole") ?: false
                val subtitle = findViewById<TextView>(R.id.tvChatSubtitle)
                if (isOtherTyping) {
                    subtitle.text = "Typing..."
                } else {
                    subtitle.text = doc.getString("jobTitle") ?: ""
                }
            }
    }

    private fun startReply(message: com.example.jobmatrix.model.ChatMessage) {
        replyToMessage = message
        val myUid = auth.currentUser?.uid ?: ""
        val senderLabel = if (message.senderId == myUid) "You" else findViewById<TextView>(R.id.tvChatTitle).text.toString()
        findViewById<TextView>(R.id.tvReplySender).text = senderLabel
        findViewById<TextView>(R.id.tvReplyText).text = message.text
        replyBar.alpha = 0f
        replyBar.visibility = android.view.View.VISIBLE
        replyBar.animate().alpha(1f).setDuration(200).start()
    }

    private fun cancelReply() {
        replyToMessage = null
        replyBar.animate().alpha(0f).setDuration(150)
            .withEndAction { replyBar.visibility = android.view.View.GONE }.start()
    }

    private fun scrollToMessage(messageId: String) {
        val index = listItems.indexOfFirst { it is ChatListItem.MessageItem && it.message.messageId == messageId }
        if (index == -1) return
        recyclerView.smoothScrollToPosition(index)
        recyclerView.postDelayed({
            val vh = recyclerView.findViewHolderForAdapterPosition(index)
            vh?.itemView?.let { row ->
                val original = row.background
                row.setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(
                    androidx.core.content.ContextCompat.getColor(this, R.color.color_accent), 60
                ))
                row.alpha = 0.7f
                row.animate().alpha(1f).setDuration(200).start()
                row.postDelayed({
                    row.animate().alpha(1f).setDuration(300).withEndAction {
                        row.background = original
                    }.start()
                }, 500)
            }
        }, 450)
    }
}