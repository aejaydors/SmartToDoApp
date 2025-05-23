package com.example.smarttodoapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.util.UUID

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var signupButton: Button
    private lateinit var progressBar: ProgressBar

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_login)
            initializeViews()
            setupClickListeners()
            checkExistingSession()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeViews() {
        try {
            usernameEditText = findViewById(R.id.usernameEditText)
            emailEditText = findViewById(R.id.emailEditText)
            passwordEditText = findViewById(R.id.etPassword)
            loginButton = findViewById(R.id.btnLogin)
            signupButton = findViewById(R.id.btnSignup)
            progressBar = findViewById(R.id.progressBar)
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error initializing views", e)
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (validateLoginInput(email, password)) {
                login(email, password)
            }
        }

        signupButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (validateSignupInput(username, email, password)) {
                signup(username, email, password)
            }
        }
    }

    private fun checkExistingSession() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                if (session != null) {
                    withContext(Dispatchers.Main) {
                        navigateToMain()
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Session check error", e)
            }
        }
    }

    private fun validateLoginInput(email: String, password: String): Boolean {
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Valid email is required"
            return false
        }

        if (password.isEmpty() || password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            return false
        }

        return true
    }

    private fun validateSignupInput(username: String, email: String, password: String): Boolean {
        if (username.isEmpty()) {
            usernameEditText.error = "Username is required"
            return false
        }

        return validateLoginInput(email, password)
    }

    @OptIn(InternalSerializationApi::class)
    @RequiresApi(Build.VERSION_CODES.O)
    private fun login(email: String, password: String) {
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Add debug logging for user ID
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                if (currentUser != null) {
                    Log.d("LoginActivity", "Current user ID: ${currentUser.id}")
                    
                    // Check if user exists in users table
                    val userExists = SupabaseClient.client.postgrest["users"]
                        .select { filter { eq("id", currentUser.id) } }
                        .decodeList<User>()
                        .isNotEmpty()
                    
                    if (!userExists) {
                        Log.d("LoginActivity", "Creating user record for existing auth user")
                        // Create user record if it doesn't exist
                        val newUser = User(
                            id = currentUser.id,
                            username = email.substringBefore("@"), // Use email prefix as username
                            created_at = OffsetDateTime.now().toString()
                        )
                        SupabaseClient.client.postgrest["users"].insert(newUser)

                        // Create default categories for the user
                        val defaultCategories = listOf(
                            "Personal",
                            "Work",
                            "Shopping",
                            "Health",
                            "Important"
                        )

                        defaultCategories.forEach { categoryName ->
                            val category = Category(
                                id = UUID.randomUUID().toString(),
                                name = categoryName,
                                user_id = currentUser.id,
                                is_system_default = false,
                                created_at = OffsetDateTime.now().toString()
                            )
                            SupabaseClient.client.postgrest["categories"].insert(category)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    showMessage("Login successful!")
                    navigateToMain()
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Login error", e)
                withContext(Dispatchers.Main) {
                    showError("Login failed: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                }
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    @RequiresApi(Build.VERSION_CODES.O)
    private fun signup(username: String, email: String, password: String) {
        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // First sign up the user with auth
                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Get the current session
                SupabaseClient.client.auth.currentSessionOrNull()
                    ?: throw Exception("Failed to get session after signup")

                // Get the current user
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("Failed to get user after signup")

                try {
                    // Create the user record
                    val newUser = User(
                        id = currentUser.id,
                        username = username,
                        created_at = OffsetDateTime.now().toString()
                    )

                    // Insert the user record
                    SupabaseClient.client.postgrest["users"].insert(newUser)

                    // Create default categories for the user
                    val defaultCategories = listOf(
                        "Personal",
                        "Work",
                        "School"
                    )

                    // Insert user-specific categories
                    defaultCategories.forEach { categoryName ->
                        val category = Category(
                            id = UUID.randomUUID().toString(),
                            name = categoryName,
                            user_id = currentUser.id,
                            is_system_default = false,
                            created_at = OffsetDateTime.now().toString()
                        )
                        SupabaseClient.client.postgrest["categories"].insert(category)
                    }

                    withContext(Dispatchers.Main) {
                        showMessage("Signup successful!")
                        navigateToMain()
                    }
                } catch (e: Exception) {
                    // If user record creation fails, clean up the auth user
                    try {
                        SupabaseClient.client.auth.signOut()
                    } catch (e2: Exception) {
                        Log.e("Signup", "Failed to cleanup auth user after failed signup", e2)
                    }
                    throw Exception("Failed to create user profile: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Signup error", e)
                withContext(Dispatchers.Main) {
                    showError("Signup failed: ${e.message}")
                    showLoading(false)
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.isVisible = show
        loginButton.isEnabled = !show
        signupButton.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

