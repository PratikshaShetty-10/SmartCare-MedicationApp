package com.yourteam.smartcare

import android.app.Application
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class SmartCareApp : Application() {
    companion object {
        // Supabase client for database and functions
        lateinit var supabase: io.github.jan.supabase.SupabaseClient

        // Standard HttpClient for custom API repositories (like VerificationRepository)
        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Supabase
        supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Functions)
        }
    }
}