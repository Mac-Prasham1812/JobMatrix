package com.example.jobmatrix.student

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jobmatrix.app.R

class ApplicationTimelineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_timeline)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        val jobTitle = intent.getStringExtra("jobTitle") ?: ""
        val companyName = intent.getStringExtra("companyName") ?: ""
        val status = intent.getStringExtra("status") ?: "Applied"
        val appliedAt = intent.getLongExtra("appliedAt", 0L)
        val inReviewAt = intent.getLongExtra("inReviewAt", 0L)
        val shortlistedAt = intent.getLongExtra("shortlistedAt", 0L)
        val rejectedAt = intent.getLongExtra("rejectedAt", 0L)
        val applicationId = intent.getStringExtra("applicationId") ?: ""

        findViewById<TextView>(R.id.tvJobTitle).text = jobTitle
        findViewById<TextView>(R.id.tvCompanyName).text = companyName

        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)
        tvAvatar.text = getInitials(companyName)
        val palette = listOf(R.color.badge_purple, R.color.badge_green, R.color.badge_teal, R.color.badge_orange)
        val color = ContextCompat.getColor(this, palette[Math.abs(companyName.hashCode()) % palette.size])
        (tvAvatar.background.mutate() as android.graphics.drawable.GradientDrawable).setColor(color)

        setupStatusBanner(status)

        val banner = findViewById<androidx.cardview.widget.CardView>(R.id.statusBanner)
        banner.scaleX = 0.92f
        banner.scaleY = 0.92f
        banner.alpha = 0f
        banner.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start()

        val steps = mutableListOf<Triple<String, Long, Boolean>>()
        steps.add(Triple("Applied", appliedAt, true))

        if (status == "Rejected") {
            steps.add(Triple("Rejected", rejectedAt, rejectedAt > 0))
        } else {
            steps.add(Triple("In Review", inReviewAt, inReviewAt > 0))
            steps.add(Triple("Shortlisted", shortlistedAt, shortlistedAt > 0))
        }

        val container = findViewById<android.widget.LinearLayout>(R.id.timelineContainer)
        val inflater = LayoutInflater.from(this)

        val lastDoneIndex = steps.indexOfLast { it.third }

        steps.forEachIndexed { index, (title, timestamp, done) ->
            val stepView = inflater.inflate(R.layout.item_timeline_step, container, false)
            val currentBadge = stepView.findViewById<TextView>(R.id.tvCurrentStepBadge)
            currentBadge.visibility = if (index == lastDoneIndex && status != "Shortlisted" && status != "Rejected")
                android.view.View.VISIBLE else android.view.View.GONE
            stepView.findViewById<TextView>(R.id.tvStepTitle).text = title
            stepView.findViewById<TextView>(R.id.tvStepDate).text =
                if (done && timestamp > 0) DateFormat.format("dd MMM yyyy, hh:mm a", timestamp).toString()
                else "Pending"

            val dot = stepView.findViewById<android.view.View>(R.id.dotStep)
            val icon = stepView.findViewById<ImageView>(R.id.ivStepIcon)
            val line = stepView.findViewById<android.view.View>(R.id.lineStep)
            val dotColor = if (done) getStatusColor(title) else R.color.color_divider
            (dot.background.mutate() as android.graphics.drawable.GradientDrawable)
                .setColor(ContextCompat.getColor(this, dotColor))

            // Line fills with accent color if the NEXT step is also done (progress connects)
            if (index < steps.size - 1) {
                val nextDone = steps[index + 1].third
                line.setBackgroundColor(
                    ContextCompat.getColor(this, if (done && nextDone) getStatusColor(title) else R.color.color_divider)
                )
            }

            icon.setImageResource(
                when {
                    title == "Rejected" && done -> R.drawable.ic_close
                    title == "Shortlisted" && done -> R.drawable.ic_check
                    title == "In Review" && done -> R.drawable.ic_eye
                    title == "Applied" && done -> R.drawable.ic_document
                    else -> R.drawable.ic_document
                }
            )

            if (done) {
                stepView.alpha = 0f
                stepView.translationY = 16f
                stepView.animate().alpha(1f).translationY(0f)
                    .setStartDelay((index * 120).toLong()).setDuration(300).start()
            }


            if (index == steps.size - 1) {
                line.visibility = android.view.View.GONE
            }

            container.addView(stepView)
        }

        val btnMessage = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnMessageEmployer)
        if ((status == "In Review" || status == "Shortlisted") && applicationId.isNotBlank()) {
            btnMessage.visibility = android.view.View.VISIBLE
            btnMessage.setOnClickListener {
                startActivity(
                    Intent(this, com.example.jobmatrix.chat.ChatActivity::class.java)
                        .putExtra("applicationId", applicationId)
                )
            }
        }

        val jobId = intent.getStringExtra("jobId") ?: ""
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnViewJobDetails).setOnClickListener {
            startActivity(
                Intent(this, JobDetailsActivity::class.java)
                    .putExtra("jobId", jobId)
            )
        }
    }

    private data class StatusStyle(
        val fgColor: Int,
        val message: String,
        val subtext: String,
        val iconRes: Int
    )

    private fun setupStatusBanner(status: String) {
        val banner = findViewById<androidx.cardview.widget.CardView>(R.id.statusBanner)
        val icon = findViewById<ImageView>(R.id.ivStatusIcon)
        val text = findViewById<TextView>(R.id.tvStatusBannerText)
        val subtext = findViewById<TextView>(R.id.tvStatusSubtext)

        val style = when (status) {
            "Shortlisted" -> StatusStyle(
                ContextCompat.getColor(this, R.color.status_shortlisted_fg),
                getString(R.string.status_msg_shortlisted),
                getString(R.string.status_sub_shortlisted),
                R.drawable.ic_check
            )
            "Rejected" -> StatusStyle(
                ContextCompat.getColor(this, R.color.status_rejected_fg),
                getString(R.string.status_msg_rejected),
                getString(R.string.status_sub_rejected),
                R.drawable.ic_close
            )
            "In Review" -> StatusStyle(
                ContextCompat.getColor(this, R.color.status_review_fg),
                getString(R.string.status_msg_review),
                getString(R.string.status_sub_review),
                R.drawable.ic_eye
            )
            else -> StatusStyle(
                ContextCompat.getColor(this, R.color.status_applied_fg),
                getString(R.string.status_msg_applied),
                getString(R.string.status_sub_applied),
                R.drawable.ic_document
            )
        }

        banner.setCardBackgroundColor(ContextCompat.getColor(this, R.color.color_surface))
        text.setTextColor(style.fgColor)
        text.text = style.message
        subtext.text = style.subtext
        icon.setImageResource(style.iconRes)
        icon.clearColorFilter()
        icon.setColorFilter(android.graphics.Color.WHITE)
        val iconBg = findViewById<android.view.View>(R.id.statusIconBg)
        (iconBg.background.mutate() as android.graphics.drawable.GradientDrawable).setColor(style.fgColor)
    }

    private fun getStatusColor(title: String): Int = when (title) {
        "Shortlisted" -> R.color.badge_green
        "Rejected" -> R.color.red
        "In Review" -> R.color.badge_orange
        else -> R.color.color_accent
    }

    private fun getInitials(company: String): String {
        val words = company.trim().split(" ").filter { it.isNotEmpty() }
        return if (words.size >= 2) (words[0].first().toString() + words[1].first().toString()).uppercase()
        else company.take(2).uppercase()
    }
}