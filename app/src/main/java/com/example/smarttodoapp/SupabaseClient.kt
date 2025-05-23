package com.example.smarttodoapp

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://bfyrkatiwypyjuzxhsec.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJmeXJrYXRpd3lweWp1enhoc2VjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcyOTg4NzgsImV4cCI6MjA2Mjg3NDg3OH0.ca0yHuxFPwcOomMy7ebMsDLkOHUcw9QPqaryOir2XXc"
    ) {
        install(Auth)
        install(Postgrest)
    }
}
