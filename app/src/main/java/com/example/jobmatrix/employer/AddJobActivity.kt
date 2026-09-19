package com.example.jobmatrix.employer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import android.widget.ArrayAdapter
import android.widget.ListView
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class AddJobActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_job)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etCompany = findViewById<EditText>(R.id.etCompany)
        val etLocation = findViewById<EditText>(R.id.etLocation)
        val etCategory = findViewById<EditText>(R.id.etCategory)
        val etSalary = findViewById<EditText>(R.id.etSalary)
        val etExperience = findViewById<EditText>(R.id.etExperience)
        val etCompanyOverview = findViewById<EditText>(R.id.etCompanyOverview)
        val btnPostJob = findViewById<Button>(R.id.btnPostJob)
        val etSkills = findViewById<EditText>(R.id.etSkills)

        val tvOverviewCount = findViewById<TextView>(R.id.tvOverviewCount)
        val btnBack = findViewById<View>(R.id.btnBack)


        val ivCategoryArrow = findViewById<View>(R.id.ivCategoryArrow)
        val ivExperienceArrow = findViewById<View>(R.id.ivExperienceArrow)

        val categoryList = listOf(
            "Development",
            "Design",
            "Marketing",
            "Sales",
            "Finance",
            "Human Resources",
            "Customer Support",
            "Operations",
            "Other"
        )

        val experienceList = listOf(
            "Fresher",
            "1 - 3 Years",
            "2 - 4 Years",
            "3 - 5 Years",
            "5+ Years",
            "Other"
        )

        val onExperienceSelected: (String) -> Unit = { selected ->
            if (selected == "Other") {
                etExperience.setText("")
                etExperience.isFocusable = true
                etExperience.isFocusableInTouchMode = true
                etExperience.isCursorVisible = true
                etExperience.requestFocus()
            } else {
                etExperience.isFocusable = false
                etExperience.isCursorVisible = false
                etExperience.setText(selected)
            }
        }

        val onCategorySelected: (String) -> Unit = { selected ->
            if (selected == "Other") {
                etCategory.setText("")
                etCategory.isFocusable = true
                etCategory.isFocusableInTouchMode = true
                etCategory.isCursorVisible = true
                etCategory.requestFocus()
            } else {
                etCategory.isFocusable = false
                etCategory.isCursorVisible = false
                etCategory.setText(selected)
            }
        }
        etCategory.setOnClickListener {
            if (etCategory.isFocusable && etCategory.isFocusableInTouchMode) return@setOnClickListener
            showBottomSheetSelector("Select Category", categoryList, onCategorySelected)
        }

        etExperience.setOnClickListener {
            if (etExperience.isFocusable && etExperience.isFocusableInTouchMode) return@setOnClickListener
            showBottomSheetSelector("Select Experience", experienceList, onExperienceSelected)
        }

        ivCategoryArrow.setOnClickListener {
            showBottomSheetSelector("Select Category", categoryList, onCategorySelected)
        }

        ivExperienceArrow.setOnClickListener {
            showBottomSheetSelector("Select Experience", experienceList, onExperienceSelected)
        }

        btnPostJob.setOnClickListener {

            val title = etTitle.text.toString().trim()
            val company = etCompany.text.toString().trim()
            val location = etLocation.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val salary = etSalary.text.toString().trim()
            val experience = etExperience.text.toString().trim()
            val companyOverview = etCompanyOverview.text.toString().trim()
            val skillsText = etSkills.text.toString().trim()


            val skillsList = skillsText.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (title.isEmpty() || company.isEmpty() || location.isEmpty()
                || category.isEmpty() || salary.isEmpty() || experience.isEmpty()
                || companyOverview.isEmpty() || skillsList.isEmpty()
            ) {
                showToast("Please fill all fields")
                return@setOnClickListener
            }

            val employerId = auth.currentUser?.uid
            if (employerId == null) {
                showToast("User not logged in")
                return@setOnClickListener
            }

            // NEW: re-check verification/disabled status live before posting (mirrors Firestore rules server-side gate)
            btnPostJob.isEnabled = false
            android.util.Log.d("JM_DEBUG", "Current UID: ${auth.currentUser?.uid}")
            db.collection("users").document(employerId).get()
                .addOnSuccessListener { employerDoc ->
                    val isDisabled = employerDoc.getBoolean("isDisabled") ?: false
                    val isVerified = employerDoc.getBoolean("isVerified") ?: false

                    if (isDisabled) {
                        showToast("Your account has been disabled. Contact support.")
                        btnPostJob.isEnabled = true
                        return@addOnSuccessListener
                    }

                    if (!isVerified) {
                        showToast("Your account is pending verification. You'll be able to post once an admin verifies your account.")
                        btnPostJob.isEnabled = true
                        return@addOnSuccessListener
                    }

                    val jobId = db.collection("jobs").document().id

                    val jobMap = hashMapOf(
                        "jobId" to jobId,
                        "title" to title,
                        "company" to company,
                        "location" to location,
                        "category" to category,
                        "salary" to salary,
                        "experience" to experience,
                        "employerId" to employerId,
                        "companyOverview" to companyOverview,
                        "status" to "Active",
                        "createdAt" to System.currentTimeMillis(),
                        "skills" to skillsList
                    )

                    db.collection("jobs")
                        .document(jobId)
                        .set(jobMap)
                        .addOnSuccessListener {
                            showToast("Job Posted Successfully")
                            notifyAdmins("NewJob", "New Job Posted", "$title at $company", jobId)
                            notifyMatchingStudents(jobId, title, company, skillsList)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            btnPostJob.isEnabled = true
                            android.util.Log.e("JM_DEBUG", "Job write failed", e)
                            showToast(e.message ?: "Something went wrong")
                        }
                }
                .addOnFailureListener { e ->
                    btnPostJob.isEnabled = true
                    android.util.Log.e("JM_DEBUG", "Employer doc read failed", e)
                    showToast("Could not verify account status. Try again.")
                }
        }

        btnBack.setOnClickListener {
            finish()
        }

        etCompanyOverview.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvOverviewCount.text = "${s?.length ?: 0} / 500"
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun showBottomSheetSelector(
        title: String,
        options: List<String>,
        onSelected: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_selector, null)

        val tvSheetTitle = view.findViewById<TextView>(R.id.tvSheetTitle)
        val listOptions = view.findViewById<ListView>(R.id.listOptions)

        tvSheetTitle.text = title

        val adapter = ArrayAdapter(
            this,
            R.layout.item_selector_option,
            R.id.tvOption,
            options
        )

        listOptions.adapter = adapter

        listOptions.setOnItemClickListener { _, _, position, _ ->
            onSelected(options[position])
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }
    private fun notifyAdmins(type: String, title: String, message: String, refId: String) {
        val notif = hashMapOf(
            "type" to type,
            "title" to title,
            "message" to message,
            "refId" to refId,
            "createdAt" to System.currentTimeMillis(),
            "isRead" to false
        )
        db.collection("adminNotifications").add(notif)
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

    private fun notifyMatchingStudents(
        jobId: String,
        jobTitle: String,
        companyName: String,
        jobSkills: List<String>
    ) {
        val jobSkillsLower = jobSkills.map { it.trim().lowercase() }

        db.collection("users")
            .whereEqualTo("role", "Student")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val studentSkills = (doc.get("skills") as? List<*>)
                        ?.mapNotNull { it?.toString()?.trim()?.lowercase() } ?: emptyList()

                    val hasMatch = jobSkillsLower.any { studentSkills.contains(it) }
                    if (!hasMatch) continue

                    val studentId = doc.id
                    val message = "New job matches your skills: $jobTitle at $companyName"

                    val notif = hashMapOf(
                        "studentId" to studentId,
                        "recipientId" to studentId,
                        "jobId" to jobId,
                        "jobTitle" to jobTitle,
                        "companyName" to companyName,
                        "message" to message,
                        "type" to "JobMatch",
                        "createdAt" to System.currentTimeMillis(),
                        "isRead" to false
                    )

                    db.collection("notifications").add(notif)

                    val token = doc.getString("fcmToken") ?: ""
                    if (token.isNotBlank()) {
                        com.example.jobmatrix.network.RetrofitClient.api
                            .let { api ->
                                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        api.sendNotification(
                                            com.example.jobmatrix.network.NotifyRequest(
                                                token,
                                                "New Job Match!",
                                                message,
                                                jobId,
                                                "JobMatch"
                                            )
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("JM_JOBALERT", "Push failed", e)
                                    }
                                }
                            }
                    }
                }
            }
    }
}