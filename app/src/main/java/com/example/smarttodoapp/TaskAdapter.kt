package com.example.smarttodoapp

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.collections.forEach

class TaskAdapter @OptIn(InternalSerializationApi::class) constructor(
    private val tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit,
    private val coroutineScope: CoroutineScope
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    // Cache for category names
    private val categoryCache = mutableMapOf<String, String>()
    private var categoriesLoaded = false

    init {
        // Load categories when adapter is created
        loadCategories()
    }

    @SuppressLint("NotifyDataSetChanged")
    @OptIn(InternalSerializationApi::class)
    private fun loadCategories() {
        coroutineScope.launch {
            try {
                val categories = withContext(Dispatchers.IO) {
                    SupabaseClient.client
                        .postgrest["categories"]
                        .select()
                        .decodeList<Category>()
                }

                // Populate cache
                categories.forEach { category ->
                    categoryCache[category.id] = category.name
                }
                categoriesLoaded = true

                // Notify adapter to refresh all items
                notifyDataSetChanged()
            } catch (e: Exception) {
                // Log error but don't crash
                println("Failed to load categories: ${e.message}")
            }
        }
    }

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.taskTitleTextView)
        val dueDateTextView: TextView = view.findViewById(R.id.taskDueDateTextView)
        val categoryTextView: TextView = view.findViewById(R.id.taskCategoryTextView)
        val remarksTextView: TextView = view.findViewById(R.id.taskRemarksTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    @OptIn(InternalSerializationApi::class)
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        // Set task title and strike through if completed
        holder.titleTextView.text = task.title
        if (task.is_done) {
            holder.titleTextView.paintFlags = holder.titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.titleTextView.paintFlags = holder.titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Format and set due date
        try {
            val dueDate = LocalDate.parse(task.due_date)
            val formattedDate = dueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            holder.dueDateTextView.text = "Due: $formattedDate"
        } catch (e: DateTimeParseException) {
            holder.dueDateTextView.text = "Due: ${task.due_date}"
        }

        // Set category from cache or show loading state
        val categoryId = task.category_id
        if (categoriesLoaded) {
            holder.categoryTextView.text = "Category: ${categoryCache[categoryId] ?: "Unknown"}"
        } else {
            holder.categoryTextView.text = "Category: Loading..."
        }

        // Set remarks (description)
        holder.remarksTextView.text = task.description

        // Set click listener
        holder.itemView.setOnClickListener { onTaskClick(task) }
    }

    @OptIn(InternalSerializationApi::class)
    override fun getItemCount() = tasks.size
}