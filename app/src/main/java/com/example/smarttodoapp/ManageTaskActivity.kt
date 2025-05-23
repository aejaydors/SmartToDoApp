package com.example.smarttodoapp

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi

class ManageTaskActivity : AppCompatActivity() {
    private lateinit var titleEditText: TextInputEditText
    private lateinit var descriptionEditText: TextInputEditText
    private lateinit var categorySpinner: AutoCompleteTextView
    private lateinit var dueDateEditText: TextInputEditText
    @RequiresApi(Build.VERSION_CODES.O)
    private var selectedDate: LocalDate = LocalDate.now()
    private var taskId: String? = null
    @OptIn(InternalSerializationApi::class)
    private var categories = mutableListOf<Category>()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_task)

        initializeViews()
        setupDatePicker()
        verifyDatabaseAccess()
        fetchCategories()
        
        // Check if we're editing an existing task
        taskId = intent.getStringExtra("task_id")
        if (taskId != null) {
            fetchTask()
        }

        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener {
            saveTask()
        }
    }

    private fun initializeViews() {
        titleEditText = findViewById(R.id.titleEditText)
        descriptionEditText = findViewById(R.id.descriptionEditText)
        categorySpinner = findViewById(R.id.categorySpinner)
        dueDateEditText = findViewById(R.id.dueDateEditText)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupDatePicker() {
        dueDateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedDate = LocalDate.of(year, month + 1, day)
                    dueDateEditText.setText(selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun fetchCategories() {
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("No user logged in")

                categories = SupabaseClient.client
                    .postgrest["categories"]
                    .select {
                        filter {
                            or {
                                eq("user_id", currentUser.id)
                                eq("is_system_default", true)
                            }
                        }
                    }
                    .decodeList<Category>()
                    .toMutableList()

                val adapter = ArrayAdapter(
                    this@ManageTaskActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    categories.map { it.name }
                )
                categorySpinner.setAdapter(adapter)
            } catch (e: Exception) {
                Toast.makeText(this@ManageTaskActivity, "Error loading categories: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    private fun fetchTask() {
        lifecycleScope.launch {
            try {
                val task = SupabaseClient.client
                    .postgrest["tasks"]
                    .select {
                        filter { eq("id", taskId!!) }
                    }
                    .decodeSingle<Task>()

                titleEditText.setText(task.title)
                descriptionEditText.setText(task.description)
                selectedDate = LocalDate.parse(task.due_date)
                dueDateEditText.setText(selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                
                // Set selected category
                val category = categories.find { it.id == task.category_id }
                if (category != null) {
                    categorySpinner.setText(category.name, false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageTaskActivity, "Error loading task: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    private fun saveTask() {
        val title = titleEditText.text?.toString()?.trim()
        val description = descriptionEditText.text?.toString()?.trim()
        val categoryName = categorySpinner.text?.toString()?.trim()

        if (title.isNullOrEmpty()) {
            titleEditText.error = "Title is required"
            return
        }

        if (description.isNullOrEmpty()) {
            descriptionEditText.error = "Description is required"
            return
        }

        if (categoryName.isNullOrEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        val category = categories.find { it.name == categoryName }
        if (category == null) {
            Toast.makeText(this, "Invalid category selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("User not logged in")

                // Debug logging
                android.util.Log.d("SaveTask", "Current user ID: ${currentUser.id}")
                android.util.Log.d("SaveTask", "Selected category ID: ${category.id}")
                android.util.Log.d("SaveTask", "Selected category name: ${category.name}")

                // First verify the category exists and is accessible
                val categoryExists = SupabaseClient.client
                    .postgrest["categories"]
                    .select {
                        filter {
                            and {
                                eq("id", category.id)
                                or {
                                    eq("user_id", currentUser.id)
                                    eq("is_system_default", true)
                                }
                            }
                        }
                    }
                    .decodeList<Category>()
                    .isNotEmpty()

                if (!categoryExists) {
                    throw Exception("Selected category does not exist or is not accessible")
                }

                // Create the task
                val task = Task(
                    id = taskId ?: UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    due_date = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    category_id = category.id,
                    user_id = currentUser.id,
                    is_done = false
                )

                // Debug logging
                android.util.Log.d("SaveTask", "Task to save: $task")

                if (taskId == null) {
                    // Create new task
                    SupabaseClient.client.postgrest["tasks"]
                        .insert(task)
                } else {
                    // Update existing task
                    SupabaseClient.client.postgrest["tasks"]
                        .update(task) {
                            filter { 
                                and {
                                    eq("id", taskId!!)
                                    eq("user_id", currentUser.id)
                                }
                            }
                        }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ManageTaskActivity, 
                        if (taskId == null) "Task created" else "Task updated",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("SaveTask", "Error saving task", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ManageTaskActivity,
                        "Error saving task: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalSerializationApi::class)
    private fun verifyDatabaseAccess() {
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("User not logged in")

                android.util.Log.d("DatabaseVerify", "Current user ID: ${currentUser.id}")

                // Test 1: Try to read categories
                val categories = SupabaseClient.client
                    .postgrest["categories"]
                    .select {
                        filter {
                            or {
                                eq("user_id", currentUser.id)
                                eq("is_system_default", true)
                            }
                        }
                    }
                    .decodeList<Category>()
                android.util.Log.d("DatabaseVerify", "Successfully read ${categories.size} categories")

                // Test 2: Try to read system default categories
                val defaultCategories = categories.filter { it.is_system_default }
                android.util.Log.d("DatabaseVerify", "Found ${defaultCategories.size} default categories")

                // Test 3: Try to create a test category
                val testCategory = Category(
                    id = UUID.randomUUID().toString(),
                    name = "Test Category ${System.currentTimeMillis()}",
                    user_id = currentUser.id,
                    is_system_default = false
                )

                try {
                    SupabaseClient.client
                        .postgrest["categories"]
                        .insert(testCategory)
                    android.util.Log.d("DatabaseVerify", "Successfully created test category")

                    // Clean up - delete the test category
                    SupabaseClient.client
                        .postgrest["categories"]
                        .delete {
                            filter { 
                                and {
                                    eq("id", testCategory.id)
                                    eq("user_id", currentUser.id)
                                }
                            }
                        }
                    android.util.Log.d("DatabaseVerify", "Successfully deleted test category")
                } catch (e: Exception) {
                    android.util.Log.e("DatabaseVerify", "Error with category operations", e)
                    throw Exception("Category operations failed: ${e.message}")
                }

                // Test 4: Try to create a test task with a default category
                if (defaultCategories.isNotEmpty()) {
                    val testTask = Task(
                        id = UUID.randomUUID().toString(),
                        title = "Test Task",
                        description = "Test Description",
                        due_date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        category_id = defaultCategories.first().id,
                        user_id = currentUser.id,
                        is_done = false
                    )

                    try {
                        SupabaseClient.client
                            .postgrest["tasks"]
                            .insert(testTask)
                        android.util.Log.d("DatabaseVerify", "Successfully created test task")

                        // Clean up - delete the test task
                        SupabaseClient.client
                            .postgrest["tasks"]
                            .delete {
                                filter { 
                                    and {
                                        eq("id", testTask.id)
                                        eq("user_id", currentUser.id)
                                    }
                                }
                            }
                        android.util.Log.d("DatabaseVerify", "Successfully deleted test task")
                    } catch (e: Exception) {
                        android.util.Log.e("DatabaseVerify", "Error with task operations", e)
                        throw Exception("Task operations failed: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ManageTaskActivity, 
                        "Database permissions verified successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("DatabaseVerify", "Error verifying database access", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ManageTaskActivity,
                        "Database verification failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
