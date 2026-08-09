package com.example.jobmatrix.settings

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.jobmatrix.app.R

class ChangePasswordActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val etCurrent = findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNew = findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnUpdate = findViewById<MaterialButton>(R.id.btnChangePassword)

        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val user = auth.currentUser
        val isGoogleOnly = user?.providerData?.none { it.providerId == "password" } ?: false

        if (isGoogleOnly) {
            showToast("This account uses Google Sign-In. Password change isn't applicable.")
            finish()
            return
        }

        btnUpdate.setOnClickListener {
            val current = etCurrent.text.toString().trim()
            val newPass = etNew.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                showToast("Please fill all fields")
                return@setOnClickListener
            }

            if (newPass.length < 6) {
                etNew.error = "Password must be at least 6 characters"
                etNew.requestFocus()
                return@setOnClickListener
            }

            if (newPass != confirm) {
                etConfirm.error = "Passwords do not match"
                etConfirm.requestFocus()
                return@setOnClickListener
            }

            btnUpdate.text = "Updating..."
            btnUpdate.isEnabled = false

            val email = user?.email ?: ""
            val credential = EmailAuthProvider.getCredential(email, current)

            user?.reauthenticate(credential)
                ?.addOnSuccessListener {
                    user.updatePassword(newPass)
                        .addOnSuccessListener {
                            showToast("Password updated successfully")
                            finish()
                        }
                        .addOnFailureListener { e ->
                            resetButton(btnUpdate)
                            showToast(e.message ?: "Failed to update password")
                        }
                }
                ?.addOnFailureListener {
                    resetButton(btnUpdate)
                    showToast("Current password is incorrect")
                }
        }
    }

    private fun resetButton(btn: MaterialButton) {
        btn.text = "UPDATE PASSWORD"
        btn.isEnabled = true
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