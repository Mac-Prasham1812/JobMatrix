package com.example.jobmatrix.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.employer.EmployerDashboardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class EmployerRegisterActivity : AppCompatActivity() {

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
        val registerContainer = findViewById<LinearLayout>(R.id.registerContainer)
        val logoMark = findViewById<LinearLayout>(R.id.logoMark)

        // Relabel for employer context
        etName.hint = "Company / HR Name"
        etPhone.hint = "Company Phone"

        // Entrance animations
        logoMark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_logo_entrance))
        registerContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_entrance))

        tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnRegister.setOnClickListener {

            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val password = etPassword.text.toString().trim()

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

            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            // Firebase Auth — creates a brand new user, result.user is guaranteed
            // to be THIS new account, never a stale/previous session's user.
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->

                    // Pulled directly from the AuthResult, never from auth.currentUser.
                    // This is what prevents the UID-mismatch bug seen with manual entries.
                    val uid = result.user!!.uid

                    val userMap = hashMapOf(
                        "uid" to uid,
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "role" to "Employer",   // fixed role
                        "createdAt" to System.currentTimeMillis()
                    )

                    // Document ID is the same uid — guarantees doc ID and uid field
                    // always match, which is exactly what was broken with the
                    // manually-created Firestore entry.
                    db.collection("users").document(uid).set(userMap)
                        .addOnSuccessListener {
                            progressBar.visibility = View.GONE
                            showToast("Registration successful")

                            startActivity(Intent(this, EmployerDashboardActivity::class.java))
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