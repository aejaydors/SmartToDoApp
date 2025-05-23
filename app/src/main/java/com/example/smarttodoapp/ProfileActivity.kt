package com.example.smarttodoapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Import data models
import com.example.smarttodoapp.Task
import com.example.smarttodoapp.User
import kotlinx.serialization.InternalSerializationApi

class ProfileActivity : AppCompatActivity() {
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var createdAtTextView: TextView
    private lateinit var totalTasksTextView: TextView
    private lateinit var completedTasksTextView: TextView
    private lateinit var logoutButton: MaterialButton

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initializeViews()
        setupToolbar()
        setupClickListeners()
        loadUserProfile()
        loadTaskStatistics()
    }

    private fun initializeViews() {
        usernameTextView = findViewById(R.id.usernameTextView)
        emailTextView = findViewById(R.id.emailTextView)
        createdAtTextView = findViewById(R.id.createdAtTextView)
        totalTasksTextView = findViewById(R.id.totalTasksTextView)
        completedTasksTextView = findViewById(R.id.completedTasksTextView)
        logoutButton = findViewById(R.id.logoutButton)
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Profile"
    }

    private fun setupClickListeners() {
        logoutButton.setOnClickListener {
            logout()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    @SuppressLint("SetTextI18n")
    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("No user logged in")

                // Get user details from the users table
                val userDetails = SupabaseClient.client.postgrest["users"]
                    .select()
                    .decodeList<User>()
                    .firstOrNull { it.id == currentUser.id }
                    ?: throw Exception("User details not found")

                // Format the created_at date
                val createdAt = Instant.parse(userDetails.created_at)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                val formattedDate = createdAt.format(
                    DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
                )

                // Update UI
                runOnUiThread {
                    usernameTextView.text = userDetails.username
                    emailTextView.text = currentUser.email
                    createdAtTextView.text = "Member since: $formattedDate"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Error loading profile: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    @SuppressLint("SetTextI18s", "SetTextI18n")
    private fun loadTaskStatistics() {
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("No user logged in")

                // Get all tasks for the current user
                val tasks = SupabaseClient.client.postgrest["tasks"]
                    .select()
                    .decodeList<Task>()
                    .filter { it.user_id.trim() == currentUser.id }

                // Calculate statistics
                val totalTasks = tasks.size
                val completedTasks = tasks.count { it.is_done }

                // Update UI
                runOnUiThread {
                    totalTasksTextView.text = "Total Tasks: $totalTasks"
                    completedTasksTextView.text = "Completed Tasks: $completedTasks"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Error loading statistics: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun logout() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.auth.signOut()
                withContext(Dispatchers.Main) {
                    // Navigate back to login screen
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Logout error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Error logging out: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

