package com.yourteam.smartcare


import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest



object SmartCareApp {
    val supabase = createSupabaseClient(
        supabaseUrl = "https://todhbxwdftgvsbewtnuk.supabase.co",
        supabaseKey = "sb_publishable_VE3vMbU7m0amIxJ1WxI82Q_7tPfT99O"
    ) {
        install(Postgrest)
        install(Functions)
    }
}