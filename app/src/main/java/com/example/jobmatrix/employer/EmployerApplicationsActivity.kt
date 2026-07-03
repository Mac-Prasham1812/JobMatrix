package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.example.jobmatrix.network.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EmployerApplicationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmployerApplicationAdapter
    private val list = mutableListOf<ApplicationModel>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employer_applications)

        val jobId = intent.getStringExtra("jobId")
        if (jobId.isNullOrEmpty()) {
            Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.rvApplications)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EmployerApplicationAdapter(list) { resumeKey ->
            openResume(resumeKey)
        }

        recyclerView.adapter = adapter

        loadApplications(jobId)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadApplications(jobId: String) {
        db.collection("applications")
            .whereEqualTo("jobId", jobId)
            .get()
            .addOnSuccessListener { documents ->
                list.clear()
                for (doc in documents) {
                    list.add(doc.toObject(ApplicationModel::class.java))
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load applications", Toast.LENGTH_SHORT).show()
            }
    }

    // resumeKey (stored under the "resumeLink" Firestore field) is the B2
    // file key. A fresh signed URL must be fetched from the backend right
    // before opening, since signed URLs expire and are never stored directly.
    private fun openResume(resumeKey: String) {
        if (resumeKey.isBlank()) {
            Toast.makeText(this, "Resume not available", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val token = "Bearer ${getIdToken()}"
                val response = RetrofitClient.api.getResumeUrl(token, resumeKey)

                if (response.isSuccessful) {
                    val url = response.body()?.url
                    if (!url.isNullOrBlank()) {
                        openUrl(url)
                    } else {
                        Toast.makeText(this@EmployerApplicationsActivity, "Resume not available", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@EmployerApplicationsActivity, "Failed to load resume", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EmployerApplicationsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun getIdToken(): String = suspendCancellableCoroutine { cont ->
        val user = auth.currentUser
        if (user == null) {
            cont.resumeWithException(Exception("Not logged in"))
            return@suspendCancellableCoroutine
        }
        user.getIdToken(true)
            .addOnSuccessListener { result -> cont.resume(result.token ?: "") }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open resume", Toast.LENGTH_SHORT).show()
        }
    }
}