package com.yourteam.smartcare

import android.app.Application
import org.osmdroid.config.Configuration
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth // 1. Add this import
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


class SmartCareApp : Application() {
    companion object {
        lateinit var supabase: io.github.jan.supabase.SupabaseClient
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
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Functions)
            install(Auth) // 2. Add this line to install the Auth module
        }
    }
}