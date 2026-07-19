package com.example.jobmatrix.employer

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R
import android.widget.ArrayAdapter
import android.widget.ListView
import com.google.android.material.bottomsheet.BottomSheetDialog

class EditJobActivity : AppCompatActivity() {

    private lateinit var etTitle: EditText
    private lateinit var etCompany: EditText
    private lateinit var etLocation: EditText
    private lateinit var etCategory: EditText
    private lateinit var etSalary: EditText
    private lateinit var etExperience: EditText
    private lateinit var etCompanyOverview: EditText
    private lateinit var etSkills: EditText
    private lateinit var btnUpdate: Button
    private lateinit var tvOverviewCount: TextView

    private val db = FirebaseFirestore.getInstance()
    private lateinit var jobId: String






    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_job)

        val ivCategoryArrow = findViewById<View>(R.id.ivCategoryArrow)
        val ivExperienceArrow = findViewById<View>(R.id.ivExperienceArrow)


        jobId = intent.getStringExtra("jobId") ?: run {
            Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        etTitle = findViewById(R.id.etTitle)
        etCompany = findViewById(R.id.etCompany)
        etLocation = findViewById(R.id.etLocation)
        etCategory = findViewById(R.id.etCategory)
        etSalary = findViewById(R.id.etSalary)
        etExperience = findViewById(R.id.etExperience)
        etCompanyOverview = findViewById(R.id.etCompanyOverview)
        etSkills = findViewById(R.id.etSkills)
        btnUpdate = findViewById(R.id.btnUpdateJob)
        tvOverviewCount = findViewById(R.id.tvOverviewCount)

        val btnBack = findViewById<View>(R.id.btnBack)
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

        loadJobDetails()

        btnUpdate.setOnClickListener {
            updateJob()
        }


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
    }

    private fun loadJobDetails() {
        db.collection("jobs")
            .document(jobId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    etTitle.setText(doc.getString("title"))
                    etCompany.setText(doc.getString("company"))
                    etLocation.setText(doc.getString("location"))
                    etCategory.setText(doc.getString("category"))
                    etSalary.setText(doc.getString("salary"))
                    etExperience.setText(doc.getString("experience"))
                    etCompanyOverview.setText(doc.getString("companyOverview"))

                    val skills = doc.get("skills") as? List<*>
                    etSkills.setText(skills?.joinToString(", ") ?: "")
                    tvOverviewCount.text = "${etCompanyOverview.text.toString().length} / 500"
                } else {
                    Toast.makeText(this, "Job not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load job details", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateJob() {
        val title = etTitle.text.toString().trim()
        val company = etCompany.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val category = etCategory.text.toString().trim()
        val salary = etSalary.text.toString().trim()
        val experience = etExperience.text.toString().trim()
        val overview = etCompanyOverview.text.toString().trim()

        val skillsText = etSkills.text.toString().trim()
        val skillsList = skillsText.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (title.isEmpty() || company.isEmpty() || location.isEmpty()
            || category.isEmpty() || salary.isEmpty() || experience.isEmpty()
            || overview.isEmpty() || skillsList.isEmpty()
        ) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val updateMap = hashMapOf<String, Any>(
            "title" to title,
            "company" to company,
            "location" to location,
            "category" to category,
            "salary" to salary,
            "experience" to experience,
            "companyOverview" to overview,
            "skills" to skillsList
        )

        db.collection("jobs")
            .document(jobId)
            .update(updateMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Job updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update job", Toast.LENGTH_SHORT).show()
            }
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