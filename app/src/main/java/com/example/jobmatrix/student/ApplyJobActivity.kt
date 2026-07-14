package com.example.jobmatrix.student

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.jobmatrix.network.RetrofitClient
import com.jobmatrix.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApplyJobActivity : AppCompatActivity() {

    private val maxFileSizeBytes = 5 * 1024 * 1024L // 5 MB

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var jobId: String
    private lateinit var jobTitle: String
    private lateinit var companyName: String

    private lateinit var cardResume: CardView
    private lateinit var layoutAlreadyApplied: CardView
    private lateinit var layoutInfoNote: LinearLayout
    private lateinit var layoutPickFile: LinearLayout
    private lateinit var layoutFileChip: LinearLayout
    private lateinit var layoutProgress: LinearLayout
    private lateinit var tvFileName: TextView
    private lateinit var tvFileSize: TextView
    private lateinit var tvUploadStatus: TextView
    private lateinit var tvSubmitHint: TextView
    private lateinit var btnRemoveFile: ImageView
    private lateinit var btnSubmit: Button

    private var selectedFileUri: Uri? = null
    private var uploadedResumeKey: String? = null
    private var isUploading = false
    private var isCheckingExisting = true
    private var canSubmit = false

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> handleFilePicked(uri) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply_job)

        jobId = intent.getStringExtra("jobId") ?: ""
        jobTitle = intent.getStringExtra("jobTitle") ?: ""
        companyName = intent.getStringExtra("companyName") ?: ""

        cardResume = findViewById(R.id.cardResume)
        layoutAlreadyApplied = findViewById(R.id.layoutAlreadyApplied)
        layoutInfoNote = findViewById(R.id.layoutInfoNote)
        layoutPickFile = findViewById(R.id.layoutPickFile)
        layoutFileChip = findViewById(R.id.layoutFileChip)
        layoutProgress = findViewById(R.id.layoutProgress)
        tvFileName = findViewById(R.id.tvFileName)
        tvFileSize = findViewById(R.id.tvFileSize)
        tvUploadStatus = findViewById(R.id.tvUploadStatus)
        tvSubmitHint = findViewById(R.id.tvSubmitHint)
        btnRemoveFile = findViewById(R.id.btnRemoveFile)
        btnSubmit = findViewById(R.id.btnSubmitApplication)
        findViewById<TextView>(R.id.tvHeaderTitle).text = "Apply for $jobTitle"
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        setSubmitButtonState(false)

        layoutPickFile.visibility = View.VISIBLE
        layoutFileChip.visibility = View.GONE
        layoutProgress.visibility = View.GONE
        layoutAlreadyApplied.visibility = View.GONE


        layoutPickFile.setOnClickListener { openFilePicker() }

        btnRemoveFile.setOnClickListener { clearSelectedFile() }

        btnSubmit.setOnClickListener {
            if (canSubmit) submitApplication()


        }

        checkExistingApplication()
    }

    // Manages the submit button's enabled look in code instead of using
    // android:enabled, since the platform Button theme auto-dims disabled
    // buttons (background + text) regardless of custom drawable colors.
    private fun setSubmitButtonState(enabled: Boolean) {
        canSubmit = enabled
        btnSubmit.background = ContextCompat.getDrawable(
            this,
            if (enabled) R.drawable.bg_button else R.drawable.bg_button_disable
        )
    }

    // Checks whether the current student has already applied to this exact
    // job. If so, the picker/submit UI is replaced with an "already applied"
    // card instead of letting them submit a duplicate.
    private fun checkExistingApplication() {
        val studentId = auth.currentUser?.uid
        if (studentId == null || jobId.isEmpty()) {
            isCheckingExisting = false
            return
        }

        db.collection("applications")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("jobId", jobId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                isCheckingExisting = false
                if (!snapshot.isEmpty) {
                    showAlreadyApplied()
                }
            }
            .addOnFailureListener {
                // If the check itself fails (e.g. offline), fail open rather
                // than blocking a legitimate application — Submit will still
                // work normally.
                isCheckingExisting = false
            }
    }

//    private fun showAlreadyApplied() {
//        cardResume.visibility = View.GONE
//        layoutInfoNote.visibility = View.GONE
//        btnSubmit.visibility = View.GONE
//        tvSubmitHint.visibility = View.GONE
//        layoutAlreadyApplied.visibility = View.VISIBLE
//
//    }

    private fun showAlreadyApplied() {
        cardResume.visibility = View.GONE
        layoutInfoNote.visibility = View.GONE
        btnSubmit.visibility = View.GONE
        tvSubmitHint.visibility = View.GONE

        layoutAlreadyApplied.scaleX = 0.94f
        layoutAlreadyApplied.scaleY = 0.94f
        layoutAlreadyApplied.alpha = 0f
        layoutAlreadyApplied.visibility = View.VISIBLE
        layoutAlreadyApplied.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .start()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pickPdfLauncher.launch(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun handleFilePicked(uri: Uri) {
        val fileSize = getFileSize(uri)

        if (fileSize > maxFileSizeBytes) {
            Toast.makeText(this, "File too large. Max size is 5 MB.", Toast.LENGTH_SHORT).show()
            return
        }

        selectedFileUri = uri
        uploadedResumeKey = null

        val fileName = getFileName(uri) ?: "resume.pdf"
        tvFileName.text = fileName
        tvFileSize.text = formatFileSize(fileSize)

        showFileCard()


        setSubmitButtonState(true)
        tvSubmitHint.text = "Ready to submit"
    }

    @SuppressLint("SetTextI18n")
    private fun clearSelectedFile() {
        selectedFileUri = null
        uploadedResumeKey = null

        layoutFileChip.visibility = View.GONE
        layoutPickFile.visibility = View.VISIBLE

        setSubmitButtonState(false)
        tvSubmitHint.text = "Select a PDF resume to continue"
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    @SuppressLint("DefaultLocale")
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb < 1024) {
            String.format("%.0f KB", kb)
        } else {
            String.format("%.1f MB", kb / 1024.0)
        }
    }

    private fun submitApplication() {
        val uri = selectedFileUri
        if (uri == null) {
            Toast.makeText(this, "Please select a PDF resume", Toast.LENGTH_SHORT).show()
            return
        }

        val studentId = auth.currentUser?.uid
        if (studentId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (jobId.isEmpty()) {
            Toast.makeText(this, "Invalid job", Toast.LENGTH_SHORT).show()
            return
        }

        if (isUploading || isCheckingExisting) return
        isUploading = true

        setUploadingUi(true)

        // Final guard right before writing — covers the rare race where the
        // student double-taps Submit before the initial check finished.
        db.collection("applications")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("jobId", jobId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    isUploading = false
                    setUploadingUi(false)
                    showAlreadyApplied()
                    return@addOnSuccessListener
                }
                proceedWithUpload(uri, studentId)
            }
            .addOnFailureListener {
                // Network hiccup on the guard check — proceed anyway rather
                // than blocking a legitimate, otherwise-valid submission.
                proceedWithUpload(uri, studentId)
            }
    }

    private fun proceedWithUpload(uri: Uri, studentId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resumeKey = uploadedResumeKey ?: withContext(Dispatchers.IO) {
                    uploadToBackend(uri)
                }
                uploadedResumeKey = resumeKey

                saveApplication(studentId, resumeKey)
            } catch (e: Exception) {
                isUploading = false
                setUploadingUi(false)
                Toast.makeText(
                    this@ApplyJobActivity,
                    "Upload failed: ${e.message ?: "please try again"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setUploadingUi(uploading: Boolean) {
        if (uploading) {
            showUploading()
        } else {
            layoutProgress.visibility = View.GONE
        }

        setSubmitButtonState(!uploading && selectedFileUri != null)
        btnRemoveFile.isEnabled = !uploading
        tvUploadStatus.text = "Uploading resume..."
    }

    // Fetches a fresh Firebase ID token as a suspend call.
    private suspend fun getIdToken(): String = suspendCancellableCoroutine { cont ->
        val user = auth.currentUser
        if (user == null) {
            cont.resumeWithException(Exception("User not logged in"))
            return@suspendCancellableCoroutine
        }
        user.getIdToken(true)
            .addOnSuccessListener { result -> cont.resume(result.token ?: "") }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    // Runs on IO dispatcher — copies the picked file to a temp file, then
    // uploads it to our own backend (which stores it in Backblaze B2),
    // returning the file `key` (NOT a URL — URLs are fetched fresh later
    // via GET /resume/:key when needed).
    private suspend fun uploadToBackend(uri: Uri): String {
        val tempFile = File(cacheDir, "resume_${System.currentTimeMillis()}.pdf")

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Could not read selected file")

        val requestFile = tempFile.asRequestBody("application/pdf".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("resume", tempFile.name, requestFile)
        val token = "Bearer ${getIdToken()}"

        val response = RetrofitClient.api.uploadResume(token, body)
        tempFile.delete()

        if (!response.isSuccessful) {
            throw Exception("Upload failed (${response.code()})")
        }

        return response.body()?.key ?: throw Exception("No key returned from server")
    }

    // NOTE: Firestore field name stays "resumeLink" for compatibility with
    // existing employer/admin screens, but the value stored is now the B2
    // file key, not a direct URL. A fresh signed URL must be fetched via
    // GET /resume/:key before opening it anywhere.
    private fun saveApplication(studentId: String, resumeKey: String) {
        val applicationId = db.collection("applications").document().id

        val applicationData = hashMapOf(
            "applicationId" to applicationId,
            "jobId" to jobId,
            "jobTitle" to jobTitle,
            "companyName" to companyName,
            "studentId" to studentId,
            "resumeLink" to resumeKey,
            "status" to "Applied",
            "hasNotification" to false,
            "isRead" to false,
            "appliedAt" to System.currentTimeMillis()
        )

        db.collection("applications")
            .document(applicationId)
            .set(applicationData)
            .addOnSuccessListener {
                isUploading = false
                Toast.makeText(this, "Application submitted successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                isUploading = false
                setUploadingUi(false)
                Toast.makeText(this, "Failed to submit application", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showFileCard() {
        layoutPickFile.animate().alpha(0f).setDuration(120).withEndAction {
            layoutPickFile.visibility = View.GONE
            layoutFileChip.alpha = 0f
            layoutFileChip.translationY = 20f
            layoutFileChip.visibility = View.VISIBLE
            layoutFileChip.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .start()
        }.start()
    }

    private fun showUploading() {
        layoutProgress.alpha = 0f
        layoutProgress.visibility = View.VISIBLE
        layoutProgress.animate().alpha(1f).setDuration(180).start()
    }


}