package com.example.jobmatrix.profile

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class SkillsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var chipGroup: ChipGroup

    // Shared list used on both student skill picker and job creation form
    // (admin side) for consistent matching.
    private val allSkills = listOf(
        "Kotlin", "Java", "Firebase", "REST API", "Git", "MVVM", "Room",
        "Jetpack Compose", "React", "Node.js", "Python", "SQL", "Excel",
        "Content Writing", "Graphic Design", "UI/UX Design", "Figma",
        "Video Editing", "Photoshop", "Premiere Pro", "Digital Marketing",
        "SEO", "Data Analysis", "Communication"
    )

    private var isFirstTime = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_skills)

        isFirstTime = intent.getBooleanExtra("isFirstTime", false)
        chipGroup = findViewById(R.id.chipGroupSkills)

        findViewById<android.view.View>(R.id.headerRow).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.anim_header_entrance)
        )

        if (isFirstTime) {
            findViewById<android.widget.ImageView>(R.id.btnBack).visibility = android.view.View.GONE
        } else {
            findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener { finish() }
        }

        buildChips(emptyList())
        loadExistingSkills()

        findViewById<android.widget.Button>(R.id.btnSaveSkills).setOnClickListener {
            saveSkills()
        }
    }

    private fun buildChips(selected: List<String>) {
        chipGroup.removeAllViews()
        for (skill in allSkills) {
            val chip = Chip(this).apply {
                text = skill
                isCheckable = true
                isChecked = selected.contains(skill)
                chipBackgroundColor = androidx.core.content.ContextCompat.getColorStateList(
                    this@SkillsActivity, R.color.chip_bg_selector
                )
                setTextColor(
                    androidx.core.content.ContextCompat.getColorStateList(
                        this@SkillsActivity, R.color.chip_text_selector
                    )
                )
                setOnCheckedChangeListener { view, _ ->
                    view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(80)
                        .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(80).start() }
                        .start()
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun loadExistingSkills() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val skills = doc.get("skills") as? List<String> ?: emptyList()
                buildChips(skills)
            }
    }

    private fun saveSkills() {
        val uid = auth.currentUser?.uid ?: return
        val selected = mutableListOf<String>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) selected.add(chip.text.toString())
        }

        db.collection("users").document(uid)
            .update("skills", selected)
            .addOnSuccessListener {
                Toast.makeText(this, "Skills saved", Toast.LENGTH_SHORT).show()
                if (isFirstTime) {
                    startActivity(
                        Intent(this, com.example.jobmatrix.student.StudentDashboardActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save skills", Toast.LENGTH_SHORT).show()
            }
    }
}