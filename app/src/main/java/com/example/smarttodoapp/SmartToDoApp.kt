package com.example.smarttodoapp

import android.app.Application
import android.util.Log

class SmartToDoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Initialize Supabase
            SupabaseClient // This will trigger the object initialization
            Log.d("SmartToDoApp", "Supabase initialized successfully")
        } catch (e: Exception) {
            Log.e("SmartToDoApp", "Error initializing Supabase", e)
        }
    }
} 