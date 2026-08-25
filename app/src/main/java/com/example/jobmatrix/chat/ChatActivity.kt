package com.example.jobmatrix.chat

import android.os.Bundle
import android.view.View
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
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

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
    private var clearedAt: Long = 0L

    private var pendingAttachmentUri: android.net.Uri? = null
    private var pendingAttachmentType = ""
    private var pendingAttachmentName = ""
    private lateinit var attachPreviewBar: android.widget.LinearLayout
    private var presenceListener: ListenerRegistration? = null

    private val galleryLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handlePickedFile(it, "image") }
    }
    private val documentLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handlePickedFile(it, "file") }
    }

    private var cameraImageUri: android.net.Uri? = null

    private val cameraLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            handlePickedFile(cameraImageUri!!, "image")
        }
    }
    private val cameraPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else android.widget.Toast.makeText(this, "Camera permission required", android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        applicationId = intent.getStringExtra("applicationId") ?: ""
        if (applicationId.isEmpty()) { finish(); return }

        getSharedPreferences("jobmatrix_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("in_chat", true)
            .apply()

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
        attachPreviewBar = findViewById(R.id.attachPreviewBar)
        findViewById<ImageView>(R.id.btnAttach).setOnClickListener { showAttachSheet() }
        findViewById<ImageView>(R.id.btnCancelAttach).setOnClickListener { cancelAttachment() }
        findViewById<ImageView>(R.id.btnCancelReply).setOnClickListener { cancelReply() }
        findViewById<ImageView>(R.id.btnCancelEdit).setOnClickListener { cancelEdit() }
        recyclerView.itemAnimator?.changeDuration = 250

        recyclerView.setHasFixedSize(true)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(listItems, auth.currentUser?.uid ?: "", applicationId,
            { message -> showEditDeleteDialog(message) },
            { replyId -> scrollToMessage(replyId) },
            { message -> openAttachment(message) }
        )
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnSend).setOnClickListener { sendMessage() }
        findViewById<ImageView>(R.id.btnChatMenu).setOnClickListener { showChatMenu(it) }

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
                        listenPresence()
                    }
            }
    }

    private fun listenPresence() {
        val myUid = auth.currentUser?.uid ?: return
        val otherUid = if (myUid == studentId) employerId else studentId
        if (otherUid.isBlank()) return

        presenceListener = db.collection("users").document(otherUid)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) return@addSnapshotListener
                val isOnline = doc.getBoolean("isOnline") ?: false
                val lastSeen = doc.getLong("lastSeen") ?: 0L
                updatePresenceUI(isOnline, lastSeen)
            }
    }

    private fun updatePresenceUI(isOnline: Boolean, lastSeen: Long) {
        val subtitle = findViewById<TextView>(R.id.tvChatSubtitle)
        val dot = findViewById<android.view.View>(R.id.dotOnline)
        dot.visibility = if (isOnline) android.view.View.VISIBLE else android.view.View.GONE
        if (isOnline) {
            subtitle.text = "Online"
        } else if (lastSeen > 0) {
            subtitle.text = "Last seen ${formatLastSeen(lastSeen)}"
        }
    }

    private fun formatLastSeen(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        val min = diff / 60000
        return when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            min < 1440 -> "${min / 60}h ago"
            min < 2880 -> "yesterday"
            else -> android.text.format.DateFormat.format("dd MMM", ts).toString()
        }
    }

    // In ChatActivity.kt, replace the loadMessages() listener section:

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

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val wasAtBottom =
                    !messagesLoadedOnce ||
                            layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 2

                val oldSize = messages.size
                messages.clear()

                for (doc in snapshot.documents) {
                    doc.toObject(ChatMessage::class.java)?.let {
                        val fixedIsRead = doc.getBoolean("isRead") ?: false
                        val fixedIsDeleted = doc.getBoolean("isDeleted") ?: false

                        messages.add(
                            it.copy(
                                messageId = doc.id,
                                isRead = fixedIsRead,
                                isDeleted = fixedIsDeleted,
                                deletedFor = doc.get("deletedFor") as? List<String> ?: emptyList()
                            )
                        )
                    }
                }

                val myUid = auth.currentUser?.uid ?: ""

                // Mark received messages as read
                for (doc in snapshot.documents) {
                    val senderId = doc.getString("senderId") ?: ""
                    val isRead = doc.getBoolean("isRead") ?: false

                    if (senderId != myUid && !isRead) {
                        doc.reference.update("isRead", true)
                    }
                }

                buildListItems()

                // Use notifyItemRangeChanged to avoid triggering onBindViewHolder for unchanged items
                adapter.notifyDataSetChanged()

                emptyStateContainer.visibility =
                    if (listItems.isEmpty()) View.VISIBLE else View.GONE

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
        if (pendingAttachmentUri != null) {
            uploadAndSendAttachment()
            return
        }

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
            "replyToSender" to (replyToMessage?.let { if (it.senderId == myUid) "You" else findViewById<TextView>(R.id.tvChatTitle).text.toString() } ?: ""),
            "replyToAttachmentType" to (replyToMessage?.attachmentType ?: ""),
            "replyToAttachmentUrl" to (replyToMessage?.attachmentUrl ?: "")
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
                                                "New message from ${companyName.ifBlank { "Employer" }}",
                                                text,
                                                applicationId
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

                android.util.Log.d("JM_CHAT", "Writing employer notif: employerId=$employerId studentId=$studentId recipientId=$employerId")

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
        db.collection("users").document(employerId).get()
            .addOnSuccessListener { userDoc ->
                val token = userDoc.getString("fcmToken") ?: ""
                if (token.isNotBlank()) {
                    lifecycleScope.launch {
                        try {
                            com.example.jobmatrix.network.RetrofitClient.api
                                .sendNotification(
                                    com.example.jobmatrix.network.NotifyRequest(
                                        token,
                                        "New message from Student",
                                        text,
                                        applicationId
                                    )
                                )
                        } catch (e: Exception) {
                            android.util.Log.e("JM_CHAT", "Push notification failed", e)
                        }
                    }
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
                loadClearance { onDone() }
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

        view.findViewById<TextView>(R.id.btnDeleteForMe).setOnClickListener {
            dialog.dismiss()
            deleteForMe(message)
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
        val myUid = auth.currentUser?.uid ?: ""
        for (msg in messages.filter { it.timestamp > clearedAt && !it.deletedFor.contains(myUid) }) {
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
        getSharedPreferences("jobmatrix_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("in_chat", false)
            .apply()
        setTyping(false)
        chatDocListener?.remove()
        chatDocListener = null
        presenceListener?.remove()
        presenceListener = null

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
                }
            }
    }

    private fun startReply(message: com.example.jobmatrix.model.ChatMessage) {
        replyToMessage = message
        val myUid = auth.currentUser?.uid ?: ""
        val senderLabel = if (message.senderId == myUid) "You" else findViewById<TextView>(R.id.tvChatTitle).text.toString()
        findViewById<TextView>(R.id.tvReplySender).text = senderLabel

        val tvReplyText = findViewById<TextView>(R.id.tvReplyText)
        val ivReplyThumb = findViewById<ImageView>(R.id.ivReplyThumb)

        if (message.attachmentType == "image") {
            tvReplyText.text = "📷 Photo"
            ivReplyThumb.visibility = android.view.View.VISIBLE
            com.bumptech.glide.Glide.with(this).load(message.attachmentUrl).into(ivReplyThumb)
        } else if (message.attachmentType == "file") {
            tvReplyText.text = "📎 ${message.attachmentName}"
            ivReplyThumb.visibility = android.view.View.VISIBLE
            ivReplyThumb.setImageResource(R.drawable.ic_file)
            ivReplyThumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
            ivReplyThumb.setPadding(8, 8, 8, 8)
        } else {
            tvReplyText.text = message.text
            ivReplyThumb.visibility = android.view.View.GONE
        }

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

    private fun showChatMenu(anchor: android.view.View) {
        val wrapper = android.view.ContextThemeWrapper(this, R.style.PopupMenuTheme)
        val popup = android.widget.PopupMenu(wrapper, anchor)
        popup.menu.add("Clear Chat")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Clear Chat") confirmClearChat()
            true
        }
        popup.show()
    }

    private fun confirmClearChat() {
        val view = layoutInflater.inflate(R.layout.dialog_clear_chat, null)
        val dialog = android.app.AlertDialog.Builder(this).setView(view).setCancelable(false).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnDelete).setOnClickListener {
            dialog.dismiss()
            clearChatForMe()
        }
        dialog.show()
        dialog.window?.attributes?.windowAnimations = android.R.style.Animation_Dialog
    }

    private fun clearChatForMe() {
        val myUid = auth.currentUser?.uid ?: return
        val docId = "${applicationId}_${myUid}"
        val now = System.currentTimeMillis()
        db.collection("chatClearance").document(docId)
            .set(mapOf("clearedAt" to now))
            .addOnSuccessListener {
                clearedAt = now
                buildListItems()
                adapter.notifyDataSetChanged()
                showCustomToast("Chat cleared")
            }
    }

    private fun loadClearance(onDone: () -> Unit) {
        val myUid = auth.currentUser?.uid ?: return
        val docId = "${applicationId}_${myUid}"
        db.collection("chatClearance").document(docId).get()
            .addOnSuccessListener { doc ->
                clearedAt = doc.getLong("clearedAt") ?: 0L
                onDone()
            }
            .addOnFailureListener { onDone() }
    }

    private fun showCustomToast(message: String) {
        val view = layoutInflater.inflate(R.layout.toast_custom, null)
        view.findViewById<TextView>(R.id.tvToastMessage).text = message
        val toast = android.widget.Toast(this)
        toast.duration = android.widget.Toast.LENGTH_SHORT
        toast.view = view
        toast.setGravity(android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, 200)
        toast.show()
    }

    private fun deleteForMe(message: com.example.jobmatrix.model.ChatMessage) {
        val myUid = auth.currentUser?.uid ?: return
        db.collection("chats").document(applicationId)
            .collection("messages").document(message.messageId)
            .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(myUid))
            .addOnSuccessListener { showCustomToast("Message deleted for you") }
    }

    private fun showAttachSheet() {
        val view = layoutInflater.inflate(R.layout.bottom_sheet_attach, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(view)
        view.findViewById<android.widget.LinearLayout>(R.id.optionGallery).setOnClickListener {
            dialog.dismiss()
            galleryLauncher.launch("image/*")
        }
        view.findViewById<android.widget.LinearLayout>(R.id.optionDocument).setOnClickListener {
            dialog.dismiss()
            documentLauncher.launch("*/*")
        }

        view.findViewById<android.widget.LinearLayout>(R.id.optionCamera).setOnClickListener {
            dialog.dismiss()
            openCamera()
        }
        dialog.show()
    }

    private fun handlePickedFile(uri: android.net.Uri, type: String) {
        val fileSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        val maxSize = 100 * 1024 * 1024
        if (fileSize > maxSize) {
            android.widget.Toast.makeText(this, "File too large (max 100MB)", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        pendingAttachmentUri = uri
        pendingAttachmentType = type
        pendingAttachmentName = getFileName(uri)

        val ivThumb = findViewById<ImageView>(R.id.ivPreviewThumb)
        findViewById<TextView>(R.id.tvPreviewName).text = pendingAttachmentName

        if (type == "image") {
            ivThumb.visibility = android.view.View.VISIBLE
            com.bumptech.glide.Glide.with(this).load(uri).into(ivThumb)
        } else {
            ivThumb.visibility = android.view.View.GONE
        }

        attachPreviewBar.alpha = 0f
        attachPreviewBar.visibility = android.view.View.VISIBLE
        attachPreviewBar.animate().alpha(1f).setDuration(200).start()
    }

    private fun cancelAttachment() {
        pendingAttachmentUri = null
        pendingAttachmentType = ""
        pendingAttachmentName = ""
        attachPreviewBar.animate().alpha(0f).setDuration(150)
            .withEndAction { attachPreviewBar.visibility = android.view.View.GONE }.start()
    }

    private fun getFileName(uri: android.net.Uri): String {
        var name = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }

    private fun compressImage(uri: android.net.Uri): java.io.File {
        val inputStream = contentResolver.openInputStream(uri)
        val original = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val maxDim = 1080
        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val scaled = android.graphics.Bitmap.createScaledBitmap(
            original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true
        )

        val file = java.io.File(cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        }
        return file
    }

    private fun uploadAndSendAttachment() {
        val uri = pendingAttachmentUri ?: return

        android.widget.Toast.makeText(this, "Uploading...", android.widget.Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val tokenResult = auth.currentUser?.getIdToken(false)?.await()
                val token = "Bearer " + (tokenResult?.token ?: "")

                val fileToUpload: java.io.File = if (pendingAttachmentType == "image") {
                    compressImage(uri)
                } else {
                    val temp = java.io.File(cacheDir, pendingAttachmentName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    temp
                }

                val mediaType = (when (pendingAttachmentType) {
                    "image" -> "image/jpeg"
                    "video" -> "video/mp4"
                    else -> "application/octet-stream"
                }).toMediaTypeOrNull()
                val requestFile = fileToUpload.asRequestBody(mediaType)
                val filePart = okhttp3.MultipartBody.Part.createFormData("file", pendingAttachmentName, requestFile)
                val appIdBody = applicationId.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = com.example.jobmatrix.network.RetrofitClient.api
                    .uploadChatAttachment(token, filePart, appIdBody)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    sendAttachmentMessage(body.key, body.url, pendingAttachmentType, body.fileName, body.fileSize)
                    cancelAttachment()
                } else {
                    android.widget.Toast.makeText(this@ChatActivity, "Upload failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("JM_CHAT", "Attachment upload failed", e)
                android.widget.Toast.makeText(this@ChatActivity, "Upload failed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendAttachmentMessage(key: String, url: String, type: String, name: String, size: Long) {
        val myUid = auth.currentUser?.uid ?: return
        val role = if (myUid == studentId) "Student" else "Employer"

        val messageData = hashMapOf(
            "senderId" to myUid,
            "senderRole" to role,
            "text" to "",
            "timestamp" to System.currentTimeMillis(),
            "attachmentKey" to key,
            "attachmentUrl" to url,
            "attachmentType" to type,
            "attachmentName" to name,
            "attachmentSize" to size
        )

        db.collection("chats").document(applicationId).collection("messages").add(messageData)
        val previewText = when(type) {
            "image" -> "📷 Photo"
            "video" -> "🎥 Video"
            else -> "📎 $name"
        }
        db.collection("chats").document(applicationId)
            .update(mapOf("lastMessage" to previewText, "lastMessageAt" to System.currentTimeMillis()))

        if (role == "Employer") createStudentNotification(previewText)
        else createEmployerNotification(previewText)
    }

    private fun openAttachment(message: com.example.jobmatrix.model.ChatMessage) {
        if (message.attachmentType == "image") {
            val intent = android.content.Intent(this@ChatActivity, ImagePreviewActivity::class.java)
            intent.putExtra("imageUrl", message.attachmentUrl)
            intent.putExtra("imageKey", message.attachmentKey)
            startActivity(intent)
            return
        }
        lifecycleScope.launch {
            try {
                val token = "Bearer " + (auth.currentUser?.getIdToken(false)?.await()?.token ?: "")
                val response = com.example.jobmatrix.network.RetrofitClient.api
                    .getChatAttachmentUrl(token, message.attachmentKey)
                val freshUrl = if (response.isSuccessful) response.body()?.url ?: message.attachmentUrl else message.attachmentUrl

                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.setDataAndType(android.net.Uri.parse(freshUrl), "*/*")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(intent) } catch (e: Exception) {
                    android.widget.Toast.makeText(this@ChatActivity, "No app to open this file", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("JM_CHAT", "openAttachment refresh failed", e)
                android.widget.Toast.makeText(this@ChatActivity, "Failed to open attachment", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val file = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraLauncher.launch(cameraImageUri!!)
    }
}