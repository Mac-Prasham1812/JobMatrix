package com.example.jobmatrix.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.jobmatrix.employer.EmployerDashboardActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider


class EmployerRegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            showToast("Google sign-in failed: ${e.statusCode}")
            resetGoogleButton()
        }
    }

    private fun resetGoogleButton() {
        findViewById<TextView>(R.id.tvGoogleRegister).apply { isEnabled = true; text = "  Continue with Google" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilName).hint = "Company / HR Name"
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPhone).hint = "Company Phone"

        logoMark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_logo_entrance))
        registerContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.anim_card_entrance))

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("849887541998-6imgmbr347c5eufsso4bttnev9eoru85.apps.googleusercontent.com")
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        findViewById<TextView>(R.id.tvGoogleRegister).setOnClickListener {
            it.isEnabled = false
            (it as TextView).text = "Signing in..."
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

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

            btnRegister.text = "Registering..."
            btnRegister.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->

                    val uid = result.user!!.uid

                    val userMap = hashMapOf(
                        "uid" to uid,
                        "name" to name,
                        "email" to email,
                        "phone" to phone,
                        "role" to "Employer",
                        "createdAt" to System.currentTimeMillis(),
                        "fcmToken" to ""
                    )

                    db.collection("users").document(uid).set(userMap)
                        .addOnSuccessListener {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                                .addOnSuccessListener { t -> db.collection("users").document(uid).update("fcmToken", t) }
                            showToast("Registration successful")
                            startActivity(Intent(this, EmployerDashboardActivity::class.java))
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


    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val userMap = hashMapOf(
                    "uid" to uid,
                    "name" to (result.user?.displayName ?: ""),
                    "email" to (result.user?.email ?: ""),
                    "phone" to "",
                    "role" to "Employer",
                    "createdAt" to System.currentTimeMillis(),
                    "fcmToken" to ""
                )
                db.collection("users").document(uid).set(userMap)
                    .addOnSuccessListener {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnSuccessListener { t -> db.collection("users").document(uid).update("fcmToken", t) }
                        showToast("Registration successful")
                        startActivity(Intent(this, EmployerDashboardActivity::class.java))
                        finish()
                    }
            }
            .addOnFailureListener { showToast("Google sign-in failed") }
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