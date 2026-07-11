package com.example.jobmatrix.student

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.JobModel
import com.jobmatrix.app.R

class JobAdapter(private var jobList: List<JobModel>) :
    RecyclerView.Adapter<JobAdapter.JobViewHolder>() {

    class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvCompany: TextView = itemView.findViewById(R.id.tvCompany)
        val tvSalary: TextView = itemView.findViewById(R.id.tvSalary)
        val tvExperience: TextView = itemView.findViewById(R.id.tvExperience)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        val tvCompanyInitial: TextView = itemView.findViewById(R.id.tvCompanyInitial)
        val tvMatchScore: TextView = itemView.findViewById(R.id.tvMatchScore)
        val badgeColors = listOf(R.color.badge_purple, R.color.badge_green, R.color.badge_teal, R.color.badge_orange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = jobList[position]

        holder.tvTitle.text = job.title
        holder.tvCompany.text = job.company
        holder.tvExperience.text = "Experience: ${job.experience}"
        holder.tvSalary.text = job.salary
        holder.tvLocation.text = job.location

        // Avatar initial + colored badge
        holder.tvCompanyInitial.text = job.company.firstOrNull()?.uppercase() ?: "?"
        val bgDrawable =
            holder.tvCompanyInitial.background.mutate() as android.graphics.drawable.GradientDrawable
        val color = holder.badgeColors[position % holder.badgeColors.size]
        bgDrawable.setColor(
            androidx.core.content.ContextCompat.getColor(
                holder.itemView.context,
                color
            )
        )

        // Match score (assumes JobModel has a matchScore field; using placeholder if not)
        holder.tvMatchScore.text = "${job.matchScore}% match"

        val score = job.matchScore
        val (badgeBg, badgeText) = when {
            score >= 80 -> android.graphics.Color.parseColor("#DCFCE7") to android.graphics.Color.parseColor(
                "#16A34A"
            )

            score >= 50 -> android.graphics.Color.parseColor("#FEF3C7") to android.graphics.Color.parseColor(
                "#D97706"
            )

            else -> android.graphics.Color.parseColor("#FEE2E2") to android.graphics.Color.parseColor(
                "#DC2626"
            )
        }
        val badgeDrawable =
            holder.tvMatchScore.background.mutate() as android.graphics.drawable.GradientDrawable
        badgeDrawable.setColor(badgeBg)
        badgeDrawable.setStroke(0, badgeBg)
        holder.tvMatchScore.setTextColor(badgeText)

        val stripeColor = holder.itemView.findViewById<View>(R.id.viewStripe)
        stripeColor.setBackgroundColor(badgeText)

        // Subtle staggered fade-up as each card binds, capped so it doesn't
        // keep re-triggering oddly on fast scroll past the first screenful.
        if (position < 8) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 24f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((position * 60).toLong())
                .setDuration(280)
                .start()
        } else {
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }

        // Open Job Details
        holder.itemView.setOnClickListener {
            if (job.jobId.isEmpty()) {
                Toast.makeText(
                    holder.itemView.context,
                    "Job details not available",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(
                holder.itemView.context,
                JobDetailsActivity::class.java
            )
            intent.putExtra("jobId", job.jobId)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = jobList.size

    fun updateList(newList: List<JobModel>) {
        jobList = newList
        notifyDataSetChanged()
    }
}