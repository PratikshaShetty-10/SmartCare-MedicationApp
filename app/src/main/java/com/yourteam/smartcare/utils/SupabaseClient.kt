package com.yourteam.smartcare.utils

import com.yourteam.smartcare.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions

// This creates the central 'supabase' variable that all her files are looking for
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_KEY
) {
    install(Postgrest)
    install(Auth)
    install(Functions) // This fixes the cloud functions errors!
}