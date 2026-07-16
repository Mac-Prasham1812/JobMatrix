package com.example.jobmatrix.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import com.google.android.material.textfield.TextInputEditText

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = getSharedPreferences("prefs", MODE_PRIVATE).getInt("night_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(saved)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val tvLoginLink = findViewById<TextView>(R.id.tvLoginLink)
        val registerContainer = findViewById<LinearLayout>(R.id.registerContainer)
        val logoMark = findViewById<LinearLayout>(R.id.logoMark)

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
            btnRegister.text = "Registering..."
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
                            showToast("Registration successful")
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener {
                            btnRegister.text = "REGISTER"
                            btnRegister.isEnabled = true
                            showToast("Failed to save user data")
                        }
                }
                .addOnFailureListener { e ->
                    btnRegister.text = "REGISTER"
                    btnRegister.isEnabled = true
                    showToast(e.message ?: "Registration failed")
                }
        }
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