package com.example.jobmatrix.chat

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.jobmatrix.app.R

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var ivImage: ImageView
    private val matrix = Matrix()
    private lateinit var scaleDetector: ScaleGestureDetector
    private var scaleFactor = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTouchY = 0f
    private var startY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)
        overridePendingTransition(android.R.anim.fade_in, 0)

        val url = intent.getStringExtra("imageUrl") ?: ""
        ivImage = findViewById(R.id.ivFullImage)
        ivImage.alpha = 0f

        com.bumptech.glide.Glide.with(this).load(url).into(ivImage)
        ivImage.animate().alpha(1f).setDuration(250).start()

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1f, 5f)
                matrix.setScale(scaleFactor, scaleFactor, ivImage.width / 2f, ivImage.height / 2f)
                ivImage.imageMatrix = matrix
                return true
            }
        })

        ivImage.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    startY = event.rawY; lastTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleFactor <= 1f) {
                        val deltaY = event.rawY - lastTouchY
                        ivImage.translationY += deltaY
                        val progress = (kotlin.math.abs(event.rawY - startY) / 800f).coerceIn(0f, 1f)
                        ivImage.alpha = 1f - progress * 0.6f
                        lastTouchY = event.rawY
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (scaleFactor <= 1f && kotlin.math.abs(event.rawY - startY) > 250) {
                        finish()
                        overridePendingTransition(0, android.R.anim.fade_out)
                    } else {
                        ivImage.animate().translationY(0f).alpha(1f).setDuration(200).start()
                    }
                }
            }
            true
        }

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
}