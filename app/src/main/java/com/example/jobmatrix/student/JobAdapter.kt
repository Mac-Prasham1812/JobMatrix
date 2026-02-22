package com.example.jobmatrix.student

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvSalary: TextView = itemView.findViewById(R.id.tvSalary)
        val tvExperience: TextView = itemView.findViewById(R.id.tvExperience)
        val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        val btnApply: Button = itemView.findViewById(R.id.btnApply)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = jobList[position]

        holder.tvTitle.text = job.title
        holder.tvCompany.text = "Company: ${job.company}"
        holder.tvCategory.text = "Category: ${job.category}"
        holder.tvExperience.text = "Experience: ${job.experience}"
        holder.tvSalary.text = job.salary
        holder.tvLocation.text = "Location: ${job.location}"

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

        // Apply Job
        holder.btnApply.setOnClickListener {

            if (job.jobId.isEmpty()) {
                Toast.makeText(
                    holder.itemView.context,
                    "Job not available right now",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(
                holder.itemView.context,
                ApplyJobActivity::class.java
            )

            // PASSING ALL REQUIRED DATA
            intent.putExtra("jobId", job.jobId)
            intent.putExtra("jobTitle", job.title)        // IMPORTANT
            intent.putExtra("companyName", job.company)  // IMPORTANT

            holder.itemView.context.startActivity(intent)
        }
    }

        override fun getItemCount(): Int = jobList.size

    fun updateList(newList: List<JobModel>) {
        jobList = newList
        notifyDataSetChanged()
    }
}
