package com.example.jobmatrix.settings

import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class JobAlertSettingsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_job_alert_settings)

        val anim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.anim_header_entrance)
        findViewById<android.widget.LinearLayout>(R.id.headerRow).startAnimation(anim)

        val cardAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.anim_header_entrance)
        cardAnim.startOffset = 100
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAlerts).startAnimation(cardAnim)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val switchAlerts = findViewById<SwitchMaterial>(R.id.switchAlerts)
        val seekThreshold = findViewById<SeekBar>(R.id.seekThreshold)
        val tvThresholdBadge = findViewById<TextView>(R.id.tvThresholdBadge)

        btnBack.setOnClickListener { finish() }

        val uid = auth.currentUser?.uid ?: return

        // Load existing prefs
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val prefs = doc.get("alertPrefs") as? Map<*, *>
                val enabled = prefs?.get("enabled") as? Boolean ?: true
                val threshold = (prefs?.get("minMatchScore") as? Long)?.toInt() ?: 50

                switchAlerts.isChecked = enabled
                seekThreshold.progress = threshold
                tvThresholdBadge.text = "$threshold%"
            }

        switchAlerts.setOnCheckedChangeListener { _, isChecked ->
            savePrefs(uid, isChecked, seekThreshold.progress)
        }

        seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvThresholdBadge.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                savePrefs(uid, switchAlerts.isChecked, seekBar?.progress ?: 50)
            }
        })
    }

    private fun savePrefs(uid: String, enabled: Boolean, threshold: Int) {
        val prefs = hashMapOf(
            "enabled" to enabled,
            "minMatchScore" to threshold
        )
        db.collection("users").document(uid)
            .update("alertPrefs", prefs)
    }
}