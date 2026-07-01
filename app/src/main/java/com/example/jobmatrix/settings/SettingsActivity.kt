package com.example.jobmatrix.settings

import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.jobmatrix.app.R

class SettingsActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        val rowAbout = findViewById<LinearLayout>(R.id.rowAbout)
        val rowPrivacy = findViewById<LinearLayout>(R.id.rowPrivacy)
        val rowTerms = findViewById<LinearLayout>(R.id.rowTerms)

        // Pull the real version name + code from the manifest instead of
        // hardcoding it, so this always matches the actual build.
        tvAppVersion.text = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            "JobMatrix v${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "JobMatrix"
        }

        btnBack.setOnClickListener { finish() }

        rowAbout.setOnClickListener {
            Toast.makeText(
                this,
                "JobMatrix connects students and employers for job placements.",
                Toast.LENGTH_LONG
            ).show()
        }

        rowPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy Policy — coming soon", Toast.LENGTH_SHORT).show()
        }

        rowTerms.setOnClickListener {
            Toast.makeText(this, "Terms of Service — coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}