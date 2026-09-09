package com.example.jobmatrix.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.jobmatrix.app.R
import kotlinx.coroutines.launch

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var ivImage: com.github.chrisbanes.photoview.PhotoView
    private val snapBackRunnable = Runnable {
        if (ivImage.scale <= 1f && kotlin.math.abs(ivImage.translationY) <= 250) {
            ivImage.animate().translationY(0f).alpha(1f).setDuration(200).start()
        }
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)
        overridePendingTransition(android.R.anim.fade_in, 0)

        val url = intent.getStringExtra("imageUrl") ?: ""
        val key = intent.getStringExtra("imageKey") ?: ""
        ivImage = findViewById(R.id.ivFullImage)
        ivImage.alpha = 0f

        com.bumptech.glide.Glide.with(this)
            .load(url)
            .signature(com.bumptech.glide.signature.ObjectKey(key))
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    refreshFullImage(key)
                    return false
                }
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })
            .into(ivImage)
        ivImage.animate().alpha(1f).setDuration(250).start()

        ivImage.setOnSingleFlingListener { _, _, _, velocityY ->
            if (ivImage.scale <= 1f && velocityY > 3000) {
                finish()
                overridePendingTransition(0, android.R.anim.fade_out)
                true
            } else false
        }

        ivImage.setOnViewDragListener { _, dy ->
            if (ivImage.scale <= 1f) {
                handler.removeCallbacks(snapBackRunnable)
                ivImage.translationY += dy
                val progress = (kotlin.math.abs(ivImage.translationY) / 800f).coerceIn(0f, 1f)
                ivImage.alpha = 1f - progress * 0.6f
                if (kotlin.math.abs(ivImage.translationY) > 250) {
                    finish()
                    overridePendingTransition(0, android.R.anim.fade_out)
                }
            }
        }
        handler.postDelayed(snapBackRunnable, 100)


        findViewById<ImageView>(R.id.btnClosePreview).setOnClickListener {
            finish()
            overridePendingTransition(0, android.R.anim.fade_out)
        }

        findViewById<ImageView>(R.id.btnSaveImage).setOnClickListener {
            saveImageToDevice(url)
        }
    }

    private fun saveImageToDevice(url: String) {
        android.widget.Toast.makeText(this, "Downloading...", android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val bitmap = com.bumptech.glide.Glide.with(this)
                    .asBitmap().load(url).submit().get()

                val fileName = "JobMatrix_${System.currentTimeMillis()}.jpg"
                val resolver = contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Saved to Pictures", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Save failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun refreshFullImage(key: String) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.currentUser?.getIdToken(false)?.addOnSuccessListener { result ->
            val token = "Bearer " + result.token
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val response = com.example.jobmatrix.network.RetrofitClient.api.getChatAttachmentUrl(token, key)
                    if (response.isSuccessful && response.body() != null) {
                        val freshUrl = response.body()!!.url
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            com.bumptech.glide.Glide.with(this@ImagePreviewActivity).load(freshUrl).into(ivImage)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JM_CHAT", "Preview refresh failed", e)
                }
            }
        }
    }
}