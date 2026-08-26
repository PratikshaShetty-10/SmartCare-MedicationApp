package com.yourteam.smartcare.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login") // <--- Added Login
    object Register : Screen("register")
    object PatientHome : Screen("patient_home")
    object MedicationTracker : Screen("medication_tracker")
    object EmergencySos : Screen("emergency_sos")
    object GuardianSetup : Screen("guardian_setup")
    object GuardianDashboard : Screen("guardian_dashboard") // Base route name without parameter
    object AddPrescription : Screen("add_prescription") // <--- Added AddPrescription
    object CameraScanner : Screen("camera_scanner")
    object PharmacyLocator : Screen("pharmacy_locator")
}