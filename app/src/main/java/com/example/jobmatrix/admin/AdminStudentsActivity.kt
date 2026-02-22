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

class AdminStudentsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private val db = FirebaseFirestore.getInstance()
    private val studentList = ArrayList<StudentModel>()
    private lateinit var adapter: AdminStudentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_students)

        recyclerView = findViewById(R.id.recyclerStudents)
        progressBar = findViewById(R.id.progressStudents)
        tvEmpty = findViewById(R.id.tvEmptyStudents)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AdminStudentAdapter(studentList)
        recyclerView.adapter = adapter

        loadStudents()
    }

    private fun loadStudents() {
        progressBar.visibility = View.VISIBLE

        db.collection("users")
            .whereEqualTo("role", "Student")
            .get()
            .addOnSuccessListener { snapshot ->
                studentList.clear()

                for (doc in snapshot.documents) {
                    val student = StudentModel(
                        uid = doc.getString("uid") ?: "",
                        name = doc.getString("name") ?: "N/A",
                        email = doc.getString("email") ?: "N/A",
                        phone = doc.getString("phone") ?: "N/A"
                    )
                    studentList.add(student)
                }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                tvEmpty.visibility = if (studentList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show()
            }
    }
}
