package com.example.jobmatrix.student

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jobmatrix.app.R

class ShimmerAdapter : RecyclerView.Adapter<ShimmerAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job_shimmer, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        // No binding needed for shimmer
    }

    override fun getItemCount(): Int = 6  // Number of shimmer cards
}
