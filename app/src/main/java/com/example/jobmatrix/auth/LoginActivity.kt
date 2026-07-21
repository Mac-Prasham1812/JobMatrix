package com.example.jobmatrix.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.admin.AdminDashboardActivity
import com.example.jobmatrix.employer.EmployerDashboardActivity
import com.example.jobmatrix.student.StudentDashboardActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)
        val tvEmployerRegisterLink = findViewById<TextView>(R.id.tvEmployerRegisterLink)
        val loginContainer = findViewById<LinearLayout>(R.id.loginContainer)
        val logoMark = findViewById<LinearLayout>(R.id.logoMark)

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener { showToast("Coming soon") }
        findViewById<TextView>(R.id.tvGoogleLogin).setOnClickListener { showToast("Coming soon") }
        findViewById<TextView>(R.id.tvFacebookLogin).setOnClickListener { showToast("Coming soon") }

        // Entrance animations
        logoMark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_logo_entrance))
        loginContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_entrance))

        // Navigate to Register (Student)
        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        // Navigate to Register (Employer)
        tvEmployerRegisterLink.setOnClickListener {
            startActivity(Intent(this, EmployerRegisterActivity::class.java))
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

            btnLogin.text = "Logging in..."
            btnLogin.isEnabled = false

            // Firebase login
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid = auth.currentUser!!.uid
                    checkUserRole(uid)
                }
                .addOnFailureListener { e ->
                    btnLogin.text = "LOGIN"
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

                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        db.collection("users").document(uid).update("fcmToken", token)
                    }

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
        findViewById<Button>(R.id.btnLogin).isEnabled = true
        findViewById<Button>(R.id.btnLogin).text = "LOGIN"
    }

    private fun showToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.toast_custom, null)
        layout.findViewById<TextView>(R.id.tvToastMessage).text = message
        Toast(this).apply {
            duration = Toast.LENGTH_SHORT
            view = layout
            show()
        }
    }
}