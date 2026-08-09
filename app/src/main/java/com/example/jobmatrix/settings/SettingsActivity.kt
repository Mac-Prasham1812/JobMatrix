package com.example.jobmatrix.settings

import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButtonToggleGroup
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
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.themeToggleGroup)

        tvAppVersion.text = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            "JobMatrix v${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "JobMatrix"
        }

        btnBack.setOnClickListener { finish() }

        // Set initial toggle state based on saved preference
        val savedMode = getSharedPreferences("prefs", MODE_PRIVATE)
            .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        when (savedMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> toggleGroup.check(R.id.btnThemeLight)
            AppCompatDelegate.MODE_NIGHT_YES -> toggleGroup.check(R.id.btnThemeDark)
            else -> toggleGroup.check(R.id.btnThemeSystem)
        }

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val newMode = when (checkedId) {
                R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }

            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putInt("night_mode", newMode).apply()
            AppCompatDelegate.setDefaultNightMode(newMode)
        }

        findViewById<LinearLayout>(R.id.rowChangePassword).setOnClickListener {
            startActivity(android.content.Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.rowNotificationSettings).setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }

        rowAbout.setOnClickListener {
            Toast.makeText(this, "JobMatrix connects students and employers for job placements.", Toast.LENGTH_LONG).show()
        }
        rowPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy Policy — coming soon", Toast.LENGTH_SHORT).show()
        }
        rowTerms.setOnClickListener {
            Toast.makeText(this, "Terms of Service — coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}