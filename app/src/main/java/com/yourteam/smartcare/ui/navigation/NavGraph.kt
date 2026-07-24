package com.yourteam.smartcare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yourteam.smartcare.ui.screens.*
import com.yourteam.smartcare.ui.screens.PatientHomeScreen
import com.yourteam.smartcare.ui.screens.GuardianSetupScreen
import com.yourteam.smartcare.ui.screens.GuardianDashboardScreen
import com.yourteam.smartcare.ui.screens.MedicationTrackerScreen
import com.yourteam.smartcare.ui.screens.EmergencySosScreen
import com.yourteam.smartcare.ui.screens.PharmacyLocatorScreen
import com.yourteam.smartcare.ui.screens.CameraScannerScreen // Added explicit import

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.GuardianSetup.route
    ) {
        composable(Screen.GuardianSetup.route) {
            GuardianSetupScreen(navController = navController)
        }

        composable(
            route = Screen.GuardianDashboard.route,
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

        // FIXED: Added the missing destination route for the Camera Scanner screen
        composable(Screen.CameraScanner.route) {
            CameraScannerScreen(navController = navController)
        }
    }
}