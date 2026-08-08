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
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)

        } catch (e: ApiException) {
            showToast("Google sign-in failed")
            findViewById<LinearLayout>(R.id.tvGoogleLoginWrapper).isEnabled = true
            findViewById<TextView>(R.id.tvGoogleLogin).text = "Continue with Google"
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            checkUserRole(currentUser.uid)
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)
        val tvEmployerRegisterLink = findViewById<TextView>(R.id.tvEmployerRegisterLink)
        val loginContainer = findViewById<LinearLayout>(R.id.loginContainer)
        val logoMark = findViewById<LinearLayout>(R.id.logoMark)

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("849887541998-6imgmbr347c5eufsso4bttnev9eoru85.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        findViewById<LinearLayout>(R.id.tvGoogleLoginWrapper).setOnClickListener {
            it.isEnabled = false
            findViewById<TextView>(R.id.tvGoogleLogin).text = "Login ..."
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        logoMark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_logo_entrance))
        loginContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_entrance))

        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        tvEmployerRegisterLink.setOnClickListener {
            startActivity(Intent(this, EmployerRegisterActivity::class.java))
            finish()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

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

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            checkUserRole(uid)
                        } else {
                            showToast("Please sign up first")
                            auth.signOut()
                        }
                    }
            }
            .addOnFailureListener { showToast("Google sign-in failed") }
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