package com.example.jobmatrix.student

import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.jobmatrix.app.R

class ApplicationAdapter(private var list: List<ApplicationModel>) :
    RecyclerView.Adapter<ApplicationAdapter.VH>() {

    private val badgeColors = listOf(
        R.color.badge_purple,
        R.color.badge_green,
        R.color.badge_teal,
        R.color.badge_orange
    )

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvCompany: TextView = v.findViewById(R.id.tvCompany)
        val tvCompanyInitial: TextView = v.findViewById(R.id.tvCompanyInitial)
        val tvStatusChip: TextView = v.findViewById(R.id.tvStatusChip)
        val tvAppliedDate: TextView = v.findViewById(R.id.tvAppliedDate)
        val viewStripe: View = v.findViewById(R.id.viewStripe)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_application, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = list[position]
        val ctx = holder.itemView.context

        holder.tvTitle.text = app.jobTitle
        holder.tvCompany.text = app.companyName
        holder.tvAppliedDate.text = if (app.appliedAt > 0)
            "Applied ${DateUtils.getRelativeTimeSpanString(app.appliedAt)}"
        else "Recently applied"

        holder.tvCompanyInitial.text = getInitials(app.companyName)
        val avatarBg = holder.tvCompanyInitial.background.mutate() as android.graphics.drawable.GradientDrawable
        val avatarColor = badgeColors[Math.abs(app.companyName.hashCode()) % badgeColors.size]
        avatarBg.setColor(ContextCompat.getColor(ctx, avatarColor))

        holder.tvStatusChip.text = app.status
        val statusColor = when (app.status) {
            "Shortlisted" -> R.color.badge_green
            "Rejected" -> R.color.red
            "In Review" -> R.color.badge_orange
            else -> R.color.color_accent
        }

        val chipBg = holder.tvStatusChip.background.mutate() as android.graphics.drawable.GradientDrawable
        holder.tvStatusChip.background = ContextCompat.getDrawable(ctx, R.drawable.bg_status_shortlisted)?.mutate()
        holder.tvStatusChip.background.setTint(ContextCompat.getColor(ctx, statusColor))
        holder.tvStatusChip.setTextColor(ContextCompat.getColor(ctx, statusColor))
        holder.viewStripe.setBackgroundColor(ContextCompat.getColor(ctx, statusColor))

        if (position < 8) {
            val anim = android.view.animation.AnimationUtils.loadAnimation(ctx, R.anim.anim_card_entrance)
            anim.startOffset = (position * 60).toLong()
            holder.itemView.startAnimation(anim)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(ctx, JobDetailsActivity::class.java)
            intent.putExtra("jobId", app.jobId)
            ctx.startActivity(intent)
        }
    }

    override fun getItemCount() = list.size

    fun updateList(newList: List<ApplicationModel>) {
        list = newList
        notifyDataSetChanged()
    }

    private fun getInitials(company: String): String {
        val words = company.trim().split(" ").filter { it.isNotEmpty() }
        return if (words.size >= 2) (words[0].first().toString() + words[1].first().toString()).uppercase()
        else company.take(2).uppercase()
    }
}