package com.example.jobmatrix.profile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import com.example.jobmatrix.auth.LoginActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var btnLogout: LinearLayout
    private lateinit var btnClose: ImageView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val experienceList = listOf("Fresher", "1-2 Years", "3-5 Years", "5+ Years", "Other")

    private var currentPhotoUrl: String? = null
    private var currentPhotoKey: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { launchCrop(it) }
    }

    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultUri = com.yalantis.ucrop.UCrop.getOutput(result.data!!)
            resultUri?.let { uploadPhoto(it) }
        } else if (result.resultCode == com.yalantis.ucrop.UCrop.RESULT_ERROR) {
            showToast("Crop failed")
        }
    }

    private fun launchCrop(sourceUri: Uri) {
        val destUri = Uri.fromFile(File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg"))
        val options = com.yalantis.ucrop.UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
            setToolbarTitle("Adjust Photo")
            setToolbarColor(androidx.core.content.ContextCompat.getColor(this@ProfileActivity, R.color.color_surface))
            setStatusBarColor(androidx.core.content.ContextCompat.getColor(this@ProfileActivity, R.color.color_bg_screen))
            setToolbarWidgetColor(androidx.core.content.ContextCompat.getColor(this@ProfileActivity, R.color.color_text_primary))
            setActiveControlsWidgetColor(androidx.core.content.ContextCompat.getColor(this@ProfileActivity, R.color.color_accent))
        }
        val intent = com.yalantis.ucrop.UCrop.of(sourceUri, destUri)
            .withAspectRatio(1f, 1f)
            .withOptions(options)
            .getIntent(this)
        cropLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvRole = findViewById(R.id.tvRole)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        btnLogout = findViewById(R.id.btnLogout)
        btnClose = findViewById(R.id.btnClose)

        loadUserData()
        loadPipelineCounts()

        btnClose.setOnClickListener { finish() }

        (findViewById<View>(R.id.tvAvatarInitials).parent as View).apply {
            isClickable = true
            isFocusable = true
            foreground = androidx.core.content.ContextCompat.getDrawable(
                this@ProfileActivity, R.drawable.bg_circle_ripple
            )
            setOnClickListener {
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                    .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                    .start()
                showPhotoOptionsSheet()
            }
        }

        findViewById<LinearLayout>(R.id.rowExperience).setOnClickListener { openExperienceSheet() }

        findViewById<LinearLayout>(R.id.rowSkills).setOnClickListener {
            startActivity(Intent(this, SkillsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowApplications).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.student.MyApplicationsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowSavedJobs).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.student.SavedJobsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowResume).setOnClickListener {
            startActivity(Intent(this, com.example.jobmatrix.settings.SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowNotifications).setOnClickListener {
            startActivity(
                Intent(
                    this,
                    com.example.jobmatrix.student.NotificationActivity::class.java
                )
            )
        }
        btnLogout.setOnClickListener {
            com.example.jobmatrix.presence.PresenceManager.goOffline()
            auth.signOut()
            GoogleSignIn.getClient(this, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun loadUserData() {
        val user = auth.currentUser ?: return
        tvEmail.text = user.email ?: "No Email"

        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("name") ?: "Unknown"
                    tvName.text = name
                    tvRole.text = doc.getString("role") ?: "User"
                    tvAvatarInitials.text = name.trim().split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .take(2)
                        .joinToString("")

                    currentPhotoKey = doc.getString("photoKey")
                    currentPhotoUrl = doc.getString("photoUrl")
                    if (!currentPhotoUrl.isNullOrBlank()) {
                        showPhoto(currentPhotoUrl!!)
                    } else {
                        showInitials()
                    }
                }
            }
            .addOnFailureListener {
                showToast("Failed to load profile")
            }
    }

    private fun showPhoto(url: String) {
        ivProfilePhoto.visibility = View.VISIBLE
        tvAvatarInitials.visibility = View.INVISIBLE
        loadPhotoWithRefresh(url)
    }

    private fun showInitials() {
        ivProfilePhoto.visibility = View.GONE
        tvAvatarInitials.visibility = View.VISIBLE
    }

    private fun loadPhotoWithRefresh(url: String) {
        Glide.with(this)
            .load(url)
            .circleCrop()
            .placeholder(R.drawable.bg_circle_blue)
            .error(R.drawable.bg_circle_blue)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean {
                    val key = currentPhotoKey ?: return true
                    refreshPhotoUrl(key)
                    return true
                }
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean = false
            })
            .into(ivProfilePhoto)
    }

    private fun refreshPhotoUrl(key: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = "Bearer ${auth.currentUser?.getIdToken(false)?.await()?.token}"
                val response = com.example.jobmatrix.network.RetrofitClient.api.getProfilePhotoUrl(token, key)
                if (response.isSuccessful) {
                    val freshUrl = response.body()?.url ?: return@launch
                    db.collection("users").document(auth.currentUser!!.uid)
                        .update("photoUrl", freshUrl)
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        currentPhotoUrl = freshUrl
                        Glide.with(this@ProfileActivity).load(freshUrl).circleCrop().into(ivProfilePhoto)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("JM_PROFILE", "Photo URL refresh failed", e)
            }
        }
    }

    private fun showPhotoOptionsSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_photo_options, null)

        val optionView = view.findViewById<LinearLayout>(R.id.optionView)
        val optionChange = view.findViewById<LinearLayout>(R.id.optionChange)
        val optionRemove = view.findViewById<LinearLayout>(R.id.optionRemove)
        val changeLabel = optionChange.getChildAt(1) as TextView

        val hasPhoto = !currentPhotoUrl.isNullOrBlank()
        optionView.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        optionRemove.visibility = if (hasPhoto) View.VISIBLE else View.GONE
        changeLabel.text = if (hasPhoto) "Change Photo" else "Add Photo"

        optionView.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, com.example.jobmatrix.chat.ImagePreviewActivity::class.java)
            intent.putExtra("imageUrl", currentPhotoUrl)
            intent.putExtra("imageKey", currentPhotoKey)
            startActivity(intent)
        }
        optionChange.setOnClickListener {
            dialog.dismiss()
            pickImageLauncher.launch("image/*")
        }
        optionRemove.setOnClickListener {
            dialog.dismiss()
            removePhoto()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun uploadPhoto(uri: Uri) {
        showToast("Uploading photo...")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val compressed = compressImage(uri)
                val token = "Bearer ${auth.currentUser?.getIdToken(false)?.await()?.token}"
                val requestFile = compressed.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("photo", "photo.jpg", requestFile)

                val response = com.example.jobmatrix.network.RetrofitClient.api.uploadProfilePhoto(token, part)
                compressed.delete()

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    db.collection("users").document(auth.currentUser!!.uid)
                        .update(mapOf("photoKey" to body.key, "photoUrl" to body.url))
                        .addOnSuccessListener {
                            currentPhotoKey = body.key
                            currentPhotoUrl = body.url
                            showPhoto(body.url)
                            showToast("Photo updated")
                        }
                } else {
                    showToast("Upload failed")
                }
            } catch (e: Exception) {
                android.util.Log.e("JM_PROFILE", "Photo upload failed", e)
                showToast("Upload failed: ${e.message}")
            }
        }
    }

    private suspend fun compressImage(uri: Uri): File = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(uri)
        val original = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val maxDim = 512
        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val scaled = android.graphics.Bitmap.createScaledBitmap(
            original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true
        )

        val file = File(cacheDir, "profile_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
        }
        file
    }

    private fun removePhoto() {
        val key = currentPhotoKey ?: return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = "Bearer ${auth.currentUser?.getIdToken(false)?.await()?.token}"
                com.example.jobmatrix.network.RetrofitClient.api.deleteProfilePhoto(token, key)

                db.collection("users").document(auth.currentUser!!.uid)
                    .update(mapOf("photoKey" to "", "photoUrl" to ""))
                    .addOnSuccessListener {
                        currentPhotoKey = null
                        currentPhotoUrl = null
                        showInitials()
                        showToast("Photo removed")
                    }
            } catch (e: Exception) {
                android.util.Log.e("JM_PROFILE", "Photo remove failed", e)
                showToast("Failed to remove photo")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadPipelineCounts() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("applications").whereEqualTo("studentId", uid)
            .get()
            .addOnSuccessListener { docs ->
                val applied = docs.count { it.getString("status") == "Applied" }
                val shortlisted = docs.count { it.getString("status") == "Shortlisted" }
                val rejected = docs.count { it.getString("status") == "Rejected" }

                findViewById<TextView>(R.id.tvAppliedCount).text = applied.toString()
                findViewById<TextView>(R.id.tvInReviewCount).text = rejected.toString()
                findViewById<TextView>(R.id.tvShortlistedCount).text = shortlisted.toString()
            }
    }

    @SuppressLint("SetTextI18n")
    private fun openExperienceSheet() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_selector, null)
        view.findViewById<TextView>(R.id.tvSheetTitle).text = "Select Experience"
        val listOptions = view.findViewById<android.widget.ListView>(R.id.listOptions)
        val adapter = android.widget.ArrayAdapter(
            this,
            R.layout.item_selector_option,
            R.id.tvOption,
            experienceList
        )
        listOptions.adapter = adapter
        listOptions.setOnItemClickListener { _, _, position, _ ->
            val selected = experienceList[position]
            if (selected == "Other") {
                dialog.dismiss()
                showCustomExperienceInput()
            } else {
                saveExperience(selected)
                dialog.dismiss()
            }
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showCustomExperienceInput() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_experience_input, null)
        val etYears = view.findViewById<android.widget.EditText>(R.id.etYears)

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val years = etYears.text.toString().trim()
            if (years.isNotBlank()) {
                saveExperience("$years Years")
                dialog.dismiss()
            }
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun saveExperience(value: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("experience", value)
            .addOnSuccessListener {
                showToast("Experience updated")
            }
    }

    private fun showToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.toast_custom, null)
        layout.findViewById<TextView>(R.id.tvToastMessage).text = message
        Toast(this).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
            show()
        }
    }
}