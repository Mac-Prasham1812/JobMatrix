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
            "Operations"
        )

        val experienceList = listOf(
            "Fresher",
            "1 - 3 Years",
            "2 - 4 Years",
            "3 - 5 Years",
            "5+ Years"
        )

        etCategory.setOnClickListener {
            showBottomSheetSelector("Select Category", categoryList) { selected ->
                etCategory.setText(selected)
            }
        }

        etExperience.setOnClickListener {
            showBottomSheetSelector("Select Experience", experienceList) { selected ->
                etExperience.setText(selected)
            }
        }

        ivCategoryArrow.setOnClickListener {
            showBottomSheetSelector("Select Category", categoryList) { selected ->
                etCategory.setText(selected)
            }
        }

        ivExperienceArrow.setOnClickListener {
            showBottomSheetSelector("Select Experience", experienceList) { selected ->
                etExperience.setText(selected)
            }
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
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val employerId = auth.currentUser?.uid
            if (employerId == null) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
                    Toast.makeText(this, "Job Posted Successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
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
}