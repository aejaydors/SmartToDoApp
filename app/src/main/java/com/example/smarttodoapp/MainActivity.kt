package com.example.smarttodoapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var addTaskFab: FloatingActionButton
    private lateinit var taskAdapter: TaskAdapter
    @OptIn(InternalSerializationApi::class)
    private val tasks = mutableListOf<Task>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar) // ✅ Important!

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
        fetchTasks()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.tasksRecyclerView)
        addTaskFab = findViewById(R.id.addTaskFab)
    }

    @OptIn(InternalSerializationApi::class)
    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            tasks = tasks,
            onTaskClick = { task ->
                // Handle task click - open TaskDetailActivity
                val intent = Intent(this, TaskDetailActivity::class.java)
                intent.putExtra("task_id", task.id)
                startActivity(intent)
            },
            coroutineScope = lifecycleScope
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }
    }

    private fun setupClickListeners() {
        addTaskFab.setOnClickListener {
            startActivity(Intent(this, ManageTaskActivity::class.java))
        }
    }

    @OptIn(InternalSerializationApi::class)
    @SuppressLint("NotifyDataSetChanged")
    private fun fetchTasks() {
        lifecycleScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("No user logged in")

                val fetchedTasks = SupabaseClient.client
                    .postgrest["tasks"]
                    .select {
                        filter { eq("user_id", currentUser.id) }
                        order("due_date", Order.ASCENDING)
                        order("is_done", Order.ASCENDING)
                    }
                    .decodeList<Task>()

                tasks.clear()
                tasks.addAll(fetchedTasks)
                taskAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading tasks: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                // Navigate to profile screen
                val intent = Intent(this, ProfileActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchTasks()
    }
}
