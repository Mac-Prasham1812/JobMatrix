package com.example.jobmatrix.student

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jobmatrix.model.ApplicationModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class NotificationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private val list = mutableListOf<ApplicationModel>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        recyclerView = findViewById(R.id.rvNotifications)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationAdapter(list)
        recyclerView.adapter = adapter
        recyclerView.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_fade_in)

        loadNotifications()
    }

    private fun loadNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("applications")
            .whereEqualTo("studentId", userId)
            .whereEqualTo("hasNotification", true)
            .get()
            .addOnSuccessListener { snapshot ->
                list.clear()

                for (doc in snapshot.documents) {
                    val app = doc.toObject(ApplicationModel::class.java)
                    if (app != null) {
                        val fixedApp = app.copy(applicationId = doc.id)
                        list.add(fixedApp)
                    }
                }

                adapter.notifyDataSetChanged()
            }
    }


}
