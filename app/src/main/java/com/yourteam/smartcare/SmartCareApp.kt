package com.yourteam.smartcare

import android.app.Application

// The ": Application()" part is what was missing!
class SmartCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Any database or Supabase setup code you have should go here
    }
}