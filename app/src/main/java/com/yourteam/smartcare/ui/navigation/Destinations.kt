
package com.yourteam.smartcare.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object PatientHome : Screen("patient_home")
    object MedicationTracker : Screen("medication_tracker")
    object EmergencySos : Screen("emergency_sos")
    object GuardianSetup : Screen("guardian_setup")
    object GuardianDashboard : Screen("guardian_dashboard")
    object AddPrescription : Screen("add_prescription")
    object AdherenceReport : Screen("adherence_report")

    // New weekly scan and adherence report
    object WeeklyMedicationReport : Screen("weekly_medication_report")

    object CameraScanner : Screen("camera_scanner")
    object ExpiryDateScanner : Screen("expiry_date_scanner")
    object PharmacyLocator : Screen("pharmacy_locator")
}
