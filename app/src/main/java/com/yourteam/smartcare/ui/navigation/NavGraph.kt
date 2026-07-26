package com.yourteam.smartcare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Single wildcard import for all screens
import com.yourteam.smartcare.ui.screens.*

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.PatientHome.route
    ) {
        composable(Screen.GuardianSetup.route) {
            GuardianSetupScreen(navController = navController)
        }

        // FIXED: Appended /{medicineName} to match the argument definition
        composable(
            route = "${Screen.GuardianDashboard.route}/{medicineName}",
            arguments = listOf(navArgument("medicineName") { type = NavType.StringType })
        ) { backStackEntry ->
            val medicineName = backStackEntry.arguments?.getString("medicineName") ?: "No Medicine Set"
            GuardianDashboardScreen(navController = navController, medicineName = medicineName)
        }

        composable(Screen.PatientHome.route) {
            PatientHomeScreen(
                onTakeMedicineClick = {
                    navController.navigate(Screen.MedicationTracker.route)
                },
                onSosClick = {
                    navController.navigate(Screen.EmergencySos.route)
                },
                onPharmacyClick = {
                    navController.navigate(Screen.PharmacyLocator.route)
                },
                onScanLabelClick = {
                    navController.navigate(Screen.CameraScanner.route)
                }
            )
        }

        composable(Screen.MedicationTracker.route) {
            MedicationTrackerScreen(navController = navController)
        }

        composable(Screen.EmergencySos.route) {
            EmergencySosScreen(navController = navController)
        }

        composable(Screen.PharmacyLocator.route) {
            PharmacyLocatorScreen(navController = navController)
        }

        composable(Screen.CameraScanner.route) {
            CameraScannerScreen(navController = navController)
        }
    }
}