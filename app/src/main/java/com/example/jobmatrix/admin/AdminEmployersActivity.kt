package com.example.jobmatrix.admin

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminEmployersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private val db = FirebaseFirestore.getInstance()
    private val employerList = ArrayList<EmployerModel>()
    private lateinit var adapter: AdminEmployerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_employers)

        recyclerView = findViewById(R.id.recyclerEmployers)
        progressBar = findViewById(R.id.progressEmployers)
        tvEmpty = findViewById(R.id.tvEmptyEmployers)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminEmployerAdapter(employerList)
        recyclerView.adapter = adapter

        loadEmployers()
    }

    private fun loadEmployers() {
        progressBar.visibility = View.VISIBLE

        db.collection("users")
            .whereEqualTo("role", "Employer")
            .get()
            .addOnSuccessListener { snapshot ->
                employerList.clear()

                for (doc in snapshot.documents) {
                    val model = EmployerModel(
                        userId = doc.id,
                        name = doc.getString("name") ?: "",
                        email = doc.getString("email") ?: "",
                        companyName = doc.getString("companyName") ?: "",
                        role = doc.getString("role") ?: "Employer"
                    )
                    employerList.add(model)
                }


                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                tvEmpty.visibility = if (employerList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load employers", Toast.LENGTH_SHORT).show()
            }
    }
}
