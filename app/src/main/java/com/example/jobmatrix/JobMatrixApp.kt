package com.example.jobmatrix

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class JobMatrixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val savedMode = getSharedPreferences("prefs", MODE_PRIVATE)
            .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }
}