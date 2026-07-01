package com.example.jobmatrix.student

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jobmatrix.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ApplyJobActivity : AppCompatActivity() {

    // ---- Cloudinary config ----
    private val cloudName = "dthjujdnc"
    private val uploadPreset = "jobmatrix_unsigned"
    private val maxFileSizeBytes = 5 * 1024 * 1024L // 5 MB

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()

    private lateinit var jobId: String
    private lateinit var jobTitle: String
    private lateinit var companyName: String

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
    private var uploadedResumeUrl: String? = null
    private var isUploading = false
    private var isCheckingExisting = true

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

        layoutPickFile = findViewById(R.id.layoutPickFile)
        layoutFileChip = findViewById(R.id.layoutFileChip)
        layoutProgress = findViewById(R.id.layoutProgress)
        tvFileName = findViewById(R.id.tvFileName)
        tvFileSize = findViewById(R.id.tvFileSize)
        tvUploadStatus = findViewById(R.id.tvUploadStatus)
        tvSubmitHint = findViewById(R.id.tvSubmitHint)
        btnRemoveFile = findViewById(R.id.btnRemoveFile)
        btnSubmit = findViewById(R.id.btnSubmitApplication)

        layoutPickFile.setOnClickListener { openFilePicker() }

        btnRemoveFile.setOnClickListener { clearSelectedFile() }

        btnSubmit.setOnClickListener { submitApplication() }

        checkExistingApplication()
    }

    // Checks whether the current student has already applied to this exact
    // job. If so, the picker/submit UI is replaced with an "already applied"
    // message instead of letting them submit a duplicate.
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

    private fun showAlreadyApplied() {
        layoutPickFile.visibility = View.GONE
        layoutFileChip.visibility = View.GONE
        layoutProgress.visibility = View.GONE

        btnSubmit.isEnabled = false
        btnSubmit.text = "ALREADY APPLIED"
        tvSubmitHint.text = "You have already applied to this job."
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        pickPdfLauncher.launch(intent)
    }

    private fun handleFilePicked(uri: Uri) {
        val fileSize = getFileSize(uri)

        if (fileSize > maxFileSizeBytes) {
            Toast.makeText(this, "File too large. Max size is 5 MB.", Toast.LENGTH_SHORT).show()
            return
        }

        selectedFileUri = uri
        uploadedResumeUrl = null

        val fileName = getFileName(uri) ?: "resume.pdf"
        tvFileName.text = fileName
        tvFileSize.text = formatFileSize(fileSize)

        layoutPickFile.visibility = View.GONE
        layoutFileChip.visibility = View.VISIBLE

        btnSubmit.isEnabled = true
        tvSubmitHint.text = "Ready to submit"
    }

    private fun clearSelectedFile() {
        selectedFileUri = null
        uploadedResumeUrl = null

        layoutFileChip.visibility = View.GONE
        layoutPickFile.visibility = View.VISIBLE

        btnSubmit.isEnabled = false
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
                val resumeUrl = uploadedResumeUrl ?: withContext(Dispatchers.IO) {
                    uploadToCloudinary(uri)
                }
                uploadedResumeUrl = resumeUrl

                saveApplication(studentId, resumeUrl)
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
        layoutProgress.visibility = if (uploading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !uploading
        btnRemoveFile.isEnabled = !uploading
        tvUploadStatus.text = "Uploading resume..."
    }

    // Runs on IO dispatcher — copies the picked file to a temp file, then
    // uploads it to Cloudinary's unsigned upload endpoint, returning the
    // secure CDN URL of the uploaded PDF.
    private fun uploadToCloudinary(uri: Uri): String {
        val tempFile = File(cacheDir, "resume_${System.currentTimeMillis()}.pdf")

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Could not read selected file")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", uploadPreset)
            .addFormDataPart(
                "file",
                tempFile.name,
                tempFile.asRequestBody("application/pdf".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/raw/upload")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            tempFile.delete()

            if (!response.isSuccessful) {
                throw Exception("Cloudinary upload failed (${response.code})")
            }

            val body = response.body?.string() ?: throw Exception("Empty response from Cloudinary")
            val json = JSONObject(body)
            return json.getString("secure_url")
        }
    }

    private fun saveApplication(studentId: String, resumeUrl: String) {
        val applicationId = db.collection("applications").document().id

        val applicationData = hashMapOf(
            "applicationId" to applicationId,
            "jobId" to jobId,
            "jobTitle" to jobTitle,
            "companyName" to companyName,
            "studentId" to studentId,
            "resumeLink" to resumeUrl,
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
}