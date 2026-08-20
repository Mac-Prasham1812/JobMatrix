package com.example.jobmatrix.presence

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object PresenceManager {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null
    private const val INTERVAL_MS = 30_000L

    fun goOnline() {
        val uid = auth.currentUser?.uid ?: return
        setStatus(uid, true)
        startHeartbeat(uid)
    }

    fun goOffline() {
        val uid = auth.currentUser?.uid ?: return
        stopHeartbeat()
        setStatus(uid, false)
    }

    private fun startHeartbeat(uid: String) {
        stopHeartbeat()
        heartbeatRunnable = object : Runnable {
            override fun run() {
                setStatus(uid, true)
                handler.postDelayed(this, INTERVAL_MS)
            }
        }
        handler.postDelayed(heartbeatRunnable!!, INTERVAL_MS)
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let { handler.removeCallbacks(it) }
        heartbeatRunnable = null
    }

    private fun setStatus(uid: String, online: Boolean) {
        db.collection("users").document(uid)
            .update(
                mapOf(
                    "isOnline" to online,
                    "lastSeen" to System.currentTimeMillis()
                )
            )
    }
}