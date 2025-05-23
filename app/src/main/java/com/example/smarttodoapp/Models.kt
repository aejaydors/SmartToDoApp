package com.example.smarttodoapp

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.io.Serializable as JavaSerializable

@InternalSerializationApi @Serializable
data class User @RequiresApi(Build.VERSION_CODES.O) constructor(
    val id: String,  // UUID from Supabase
    val username: String,
    val created_at: String = OffsetDateTime.now().toString()
)

@InternalSerializationApi @RequiresApi(Build.VERSION_CODES.O)
@Serializable
data class Category(
    val id: String,  // Changed from Long to String for UUID
    val name: String,
    val user_id: String? = null,  // UUID from User
    val is_system_default: Boolean = false,
    val created_at: String = OffsetDateTime.now().toString()
)

@InternalSerializationApi @Serializable
data class Task @RequiresApi(Build.VERSION_CODES.O) constructor(
    val id: String,  // Changed from Long to String for UUID
    val title: String,
    val description: String,
    val due_date: String,  // Format: YYYY-MM-DD
    val is_done: Boolean = false,
    val category_id: String,  // Changed from Long to String for UUID
    val user_id: String,  // UUID from User
    val created_at: String = OffsetDateTime.now().toString()
) : JavaSerializable {
    companion object {
        // Helper function to create a date string in the correct format
        @SuppressLint("DefaultLocale")
        fun formatDate(year: Int, month: Int, day: Int): String {
            return String.format("%04d-%02d-%02d", year, month + 1, day)
        }
    }
} 