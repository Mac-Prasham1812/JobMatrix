package com.example.jobmatrix.student

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.profile.ProfileActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class ProfileChecklistActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_checklist)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadChecklist()
    }

    private fun loadChecklist() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val skills = doc.get("skills") as? List<*>
                val experience = doc.getString("experience")
                val phone = doc.getString("phone")
                val photoUrl = doc.getString("photoUrl")

                val skillsComplete = !skills.isNullOrEmpty()
                val experienceComplete = !experience.isNullOrBlank()
                val phoneComplete = !phone.isNullOrBlank()
                val photoComplete = !photoUrl.isNullOrBlank()

                var percent = 0
                if (skillsComplete) percent += 35
                if (experienceComplete) percent += 25
                if (phoneComplete) percent += 20
                if (photoComplete) percent += 20

                updateSummary(percent)

                bindChecklistItem(
                    rowId = R.id.rowChecklistSkills,
                    statusId = R.id.tvSkillsStatus,
                    actionIconId = R.id.ivSkillsAction,
                    completed = skillsComplete,
                    points = 35,
                    action = ProfileActivity.ACTION_SKILLS
                )

                bindChecklistItem(
                    rowId = R.id.rowChecklistExperience,
                    statusId = R.id.tvExperienceStatus,
                    actionIconId = R.id.ivExperienceAction,
                    completed = experienceComplete,
                    points = 25,
                    action = ProfileActivity.ACTION_EXPERIENCE
                )

                bindChecklistItem(
                    rowId = R.id.rowChecklistPhone,
                    statusId = R.id.tvPhoneStatus,
                    actionIconId = R.id.ivPhoneAction,
                    completed = phoneComplete,
                    points = 20,
                    action = ProfileActivity.ACTION_PHONE
                )

                bindChecklistItem(
                    rowId = R.id.rowChecklistPhoto,
                    statusId = R.id.tvPhotoStatus,
                    actionIconId = R.id.ivPhotoAction,
                    completed = photoComplete,
                    points = 20,
                    action = ProfileActivity.ACTION_PHOTO
                )
            }
    }

    private fun updateSummary(percent: Int) {
        val pb = findViewById<android.widget.ProgressBar>(R.id.pbChecklistProgress)
        val tvPercent = findViewById<TextView>(R.id.tvChecklistPercent)
        val tvStrength = findViewById<TextView>(R.id.tvChecklistStrength)
        val tvMessage = findViewById<TextView>(R.id.tvChecklistMessage)
        val summaryCard =
            findViewById<com.google.android.material.card.MaterialCardView>(
                R.id.cardChecklistSummary
            )

        val strength = when {
            percent == 100 -> "Complete"
            percent >= 70 -> "Strong"
            percent >= 40 -> "Growing"
            else -> "Basic"
        }

        val strengthColorRes = when (strength) {
            "Complete" -> R.color.status_shortlisted_fg
            "Strong" -> R.color.status_applied_fg
            "Growing" -> R.color.status_review_fg
            else -> R.color.status_rejected_fg
        }

        val message = when (strength) {
            "Complete" -> "Your profile is ready for applications and recruiters."
            "Strong" -> "You are almost recruiter-ready."
            "Growing" -> "A few more details will strengthen your profile."
            else -> "Complete your details to improve job matches."
        }

        val strengthColor =
            androidx.core.content.ContextCompat.getColor(this, strengthColorRes)

        android.animation.ValueAnimator.ofInt(pb.progress, percent).apply {
            duration = 450
            addUpdateListener {
                pb.progress = it.animatedValue as Int
            }
            start()
        }

        (pb.progressDrawable as? android.graphics.drawable.LayerDrawable)
            ?.findDrawableByLayerId(android.R.id.progress)
            ?.setTint(strengthColor)

        tvPercent.text = "$percent%"
        tvPercent.setTextColor(strengthColor)
        tvStrength.text = "Profile strength: $strength"
        tvStrength.setTextColor(strengthColor)
        tvMessage.text = message

        summaryCard.setCardBackgroundColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                R.color.color_surface
            )
        )

        summaryCard.strokeColor =
            if (percent == 100) {
                strengthColor
            } else {
                androidx.core.content.ContextCompat.getColor(
                    this,
                    R.color.color_divider
                )
            }
    }

    private fun bindChecklistItem(
        rowId: Int,
        statusId: Int,
        actionIconId: Int,
        completed: Boolean,
        points: Int,
        action: String
    ) {
        val row = findViewById<LinearLayout>(rowId)
        val tvStatus = findViewById<TextView>(statusId)
        val ivAction = findViewById<ImageView>(actionIconId)

        val completedColor = androidx.core.content.ContextCompat.getColor(
            this,
            R.color.status_shortlisted_fg
        )

        val hintColor = androidx.core.content.ContextCompat.getColor(
            this,
            R.color.color_text_hint
        )

        if (completed) {
            tvStatus.text = "Completed"
            tvStatus.setTextColor(completedColor)

            ivAction.setImageResource(R.drawable.ic_check)
            ivAction.imageTintList =
                android.content.res.ColorStateList.valueOf(completedColor)

            row.contentDescription = "Completed profile item"
        } else {
            tvStatus.text = "Complete now · +$points%"
            tvStatus.setTextColor(hintColor)

            ivAction.setImageResource(R.drawable.ic_chevron_right)
            ivAction.imageTintList =
                android.content.res.ColorStateList.valueOf(hintColor)

            row.contentDescription = "Incomplete profile item. Worth $points percent."
        }

        row.setOnClickListener {
            if (completed) {
                startActivity(Intent(this, ProfileActivity::class.java))
            } else {
                startActivity(
                    Intent(this, ProfileActivity::class.java)
                        .putExtra(ProfileActivity.EXTRA_PROFILE_ACTION, action)
                )
            }
        }
    }
}