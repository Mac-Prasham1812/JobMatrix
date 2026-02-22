package com.example.jobmatrix.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnRegister.setOnClickListener {

            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validation
            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
                showToast("Please fill all fields")
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                etPassword.error = "Password must be at least 6 characters"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            // UI state
            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            // Firebase Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->

                    val uid = result.user!!.uid

                    val userMap = hashMapOf(
                        "uid" to uid,
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "role" to "Student",   // fixed role
                        "createdAt" to System.currentTimeMillis()
                    )

                    //  Firestore
                    db.collection("users").document(uid).set(userMap)
                        .addOnSuccessListener {
                            progressBar.visibility = View.GONE
                            showToast("Registration successful")

                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                            btnRegister.isEnabled = true
                            showToast("Failed to save user data")
                        }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    showToast(e.message ?: "Registration failed")
                }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
