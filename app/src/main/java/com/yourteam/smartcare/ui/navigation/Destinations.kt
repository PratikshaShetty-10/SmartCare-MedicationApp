package com.yourteam.smartcare.ui.navigation

sealed class Screen(val route: String) {
    object PatientHome : Screen("patient_home")
    object MedicationTracker : Screen("medication_tracker")
    object EmergencySos : Screen("emergency_sos")
    object GuardianSetup : Screen("guardian_setup")
    object GuardianDashboard : Screen("guardian_dashboard/{medicineName}")
    object CameraScanner : Screen("camera_scanner")
    object PharmacyLocator : Screen("pharmacy_locator")

}