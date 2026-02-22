package com.example.jobmatrix.admin

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.jobmatrix.app.R

class AdminStudentAdapter(private val list: MutableList<StudentModel>) :
    RecyclerView.Adapter<AdminStudentAdapter.StudentViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvStudentName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvStudentEmail)
        val tvPhone: TextView = itemView.findViewById(R.id.tvStudentPhone)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteStudent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_student, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = list[position]

        holder.tvName.text = student.name
        holder.tvEmail.text = student.email
        holder.tvPhone.text = student.phone

        holder.btnDelete.setOnClickListener {
            showDeleteDialog(holder.itemView.context) {
                deleteStudent(student.uid, position, holder)
            }
        }

    }
    private fun showDeleteDialog(context: Context, onDelete: () -> Unit) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_admin_delete, null)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDelete)

        tvTitle.text = "Delete Student"
        tvMessage.text = "This will permanently delete this student and all their applications."

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.show()
    }


    private fun deleteStudent(studentId: String, position: Int, holder: StudentViewHolder) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        // Delete student's applications
        db.collection("applications")
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { apps ->
                for (doc in apps.documents) {
                    batch.delete(doc.reference)
                }

                // Delete student user account
                val userRef = db.collection("users").document(studentId)
                batch.delete(userRef)

                batch.commit()
                    .addOnSuccessListener {
                        list.removeAt(position)
                        notifyItemRemoved(position)

                        Toast.makeText(
                            holder.itemView.context,
                            "Student deleted successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            holder.itemView.context,
                            "Delete failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
    }


    override fun getItemCount(): Int = list.size
}
