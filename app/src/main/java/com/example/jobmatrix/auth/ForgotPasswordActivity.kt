package com.example.jobmatrix.auth

import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.jobmatrix.app.R

class ForgotPasswordActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val etEmail = findViewById<TextInputEditText>(R.id.etResetEmail)
        val btnSend = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSendReset)
        val tvBack = findViewById<TextView>(R.id.tvBackToLogin)

        tvBack.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Enter a valid email"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            btnSend.text = "Sending..."
            btnSend.isEnabled = false

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener {
                    showToast("If this email is registered, a reset link has been sent. Check spam folder too.")
                    finish()
                }
        }
    }

    private fun showToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.toast_custom, null)
        layout.findViewById<TextView>(R.id.tvToastMessage).text = message
        Toast(this).apply {
            duration = Toast.LENGTH_LONG
            view = layout
            show()
        }
    }
}