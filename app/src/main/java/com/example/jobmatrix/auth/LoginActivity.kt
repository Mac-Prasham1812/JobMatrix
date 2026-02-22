package com.example.jobmatrix.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.admin.AdminDashboardActivity
import com.example.jobmatrix.employer.EmployerDashboardActivity
import com.example.jobmatrix.student.StudentDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)

        // Navigate to Register
        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        btnLogin.setOnClickListener {

            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validation
            if (email.isEmpty() || password.isEmpty()) {
                showToast("Please fill all fields")
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            // Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser!!.uid
                    checkUserRole(uid)
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    showToast(e.message ?: "Login failed")
                }
        }
    }

    private fun checkUserRole(uid: String) {

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    showToast("User record not found")
                    resetUI()
                    return@addOnSuccessListener
                }

                val role = doc.getString("role")

                when (role) {
                    "Student" -> {
                        startActivity(Intent(this, StudentDashboardActivity::class.java))
                    }
                    "Employer" -> {
                        startActivity(Intent(this, EmployerDashboardActivity::class.java))
                    }
                    "Admin" -> {
                        startActivity(Intent(this, AdminDashboardActivity::class.java))
                    }
                    else -> {
                        showToast("Invalid role")
                        resetUI()
                        return@addOnSuccessListener
                    }
                }

                finish()
            }
            .addOnFailureListener {
                showToast("Failed to fetch role")
                resetUI()
            }
    }

    private fun resetUI() {
        findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
        findViewById<Button>(R.id.btnLogin).isEnabled = true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
