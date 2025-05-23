package com.example.smarttodoapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Suppress("DEPRECATION")
class TaskDetailActivity : AppCompatActivity() {
    private lateinit var titleTextView: TextView
    private lateinit var categoryTextView: TextView
    private lateinit var dueDateTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var toggleCompleteButton: MaterialButton
    private lateinit var editButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    
    private var taskId: String? = null
    @OptIn(InternalSerializationApi::class)
    private var currentTask: Task? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)

        taskId = intent.getStringExtra("task_id")
        if (taskId == null) {
            Toast.makeText(this, "Error: Task not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupClickListeners()
        fetchTaskDetails()
    }

    private fun initializeViews() {
        titleTextView = findViewById(R.id.titleTextView)
        categoryTextView = findViewById(R.id.categoryTextView)
        dueDateTextView = findViewById(R.id.dueDateTextView)
        descriptionTextView = findViewById(R.id.descriptionTextView)
        toggleCompleteButton = findViewById(R.id.toggleCompleteButton)
        editButton = findViewById(R.id.editButton)
        deleteButton = findViewById(R.id.deleteButton)
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Task Details"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    private fun setupClickListeners() {
        toggleCompleteButton.setOnClickListener {
            currentTask?.let { task ->
                toggleTaskCompletion(task)
            }
        }

        editButton.setOnClickListener {
            val intent = Intent(this, ManageTaskActivity::class.java)
            intent.putExtra("task_id", taskId)
            startActivity(intent)
        }

        deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    @SuppressLint("SetTextI18n")
    private fun fetchTaskDetails() {
        lifecycleScope.launch {
            try {
                val task = SupabaseClient.client
                    .postgrest["tasks"]
                    .select { filter { eq("id", taskId!!) } }
                    .decodeSingle<Task>()

                currentTask = task
                displayTaskDetails(task)

                // Fetch category name
                val category = task.category_id?.let { categoryId ->
                    SupabaseClient.client
                        .postgrest["categories"]
                        .select { filter { eq("id", categoryId) } }
                        .decodeSingle<Category>()
                } ?: throw Exception("Category ID is null")

                categoryTextView.text = "Category: ${category.name}"
            } catch (e: Exception) {
                Toast.makeText(this@TaskDetailActivity, "Error loading task: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    @SuppressLint("SetTextI18s", "SetTextI18n")
    private fun displayTaskDetails(task: Task) {
        titleTextView.text = task.title
        descriptionTextView.text = task.description
        
        // Format and display due date
        val dueDate = LocalDate.parse(task.due_date)
        val formattedDate = dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        dueDateTextView.text = "Due: $formattedDate"

        // Update complete button text based on task status
        toggleCompleteButton.text = if (task.is_done) "Mark as Incomplete" else "Mark as Complete"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    private fun toggleTaskCompletion(task: Task) {
        lifecycleScope.launch {
            try {
                val updatedTask = task.copy(is_done = !task.is_done)
                SupabaseClient.client.postgrest["tasks"]
                    .update(updatedTask) { filter { eq("id", taskId!!) } }

                currentTask = updatedTask
                displayTaskDetails(updatedTask)
                Toast.makeText(
                    this@TaskDetailActivity,
                    if (updatedTask.is_done) "Task marked as complete" else "Task marked as incomplete",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@TaskDetailActivity, "Error updating task: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this task?")
            .setPositiveButton("Delete") { _, _ -> deleteTask() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTask() {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.postgrest["tasks"]
                    .delete { filter { eq("id", taskId!!) } }

                Toast.makeText(this@TaskDetailActivity, "Task deleted", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@TaskDetailActivity, "Error deleting task: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()
        fetchTaskDetails() // Refresh task details when returning from edit
    }
}

