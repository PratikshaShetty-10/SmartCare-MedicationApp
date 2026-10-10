
package com.yourteam.smartcare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.ui.MedicationViewModel
import com.yourteam.smartcare.ui.screens.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun NavGraph() {
    val navController: NavHostController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        setupSmartCareRoutes(navController = navController)
    }
}

private fun NavGraphBuilder.setupSmartCareRoutes(
    navController: NavHostController
) {

    // 1. AUTHENTICATION

    composable(Screen.Login.route) {
        LoginScreen(navController = navController)
    }

    composable(Screen.Register.route) {
        RegisterScreen(navController = navController)
    }

    // 2. ELDER DASHBOARD

    composable(Screen.PatientHome.route) {
        val scope = rememberCoroutineScope()

        PatientHomeScreen(
            onTakeMedicineClick = {
                navController.navigate(Screen.MedicationTracker.route)
            },
            onPharmacyClick = {
                navController.navigate(Screen.PharmacyLocator.route)
            },
            onScanLabelClick = {
                // Existing weekly medication scan
                navController.navigate(Screen.CameraScanner.route)
            },
            onExpiryDateClick = {
                // Separate expiry-date scanning workflow
                navController.navigate(Screen.ExpiryDateScanner.route)
            },
            onLogoutClick = {
                scope.launch {
                    SupabaseClient.client.auth.signOut()

                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.PatientHome.route) {
                            inclusive = true
                        }
                    }
                }
            }
        )
    }

    // 3. GUARDIAN FLOW

    composable(Screen.GuardianSetup.route) {
        GuardianSetupScreen(navController = navController)
    }

    // Shared ViewModel for Guardian Dashboard and Add Prescription

    composable(Screen.GuardianDashboard.route) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry(
                Screen.GuardianDashboard.route
            )
        }

        val sharedViewModel: MedicationViewModel =
            viewModel(parentEntry)

        GuardianDashboardScreen(
            navController = navController,
            viewModel = sharedViewModel
        )
    }

    composable(Screen.AdherenceReport.route) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry(
                Screen.GuardianDashboard.route
            )
        }

        val sharedViewModel: MedicationViewModel =
            viewModel(parentEntry)

        AdherenceReportScreen(
            navController = navController,
            viewModel = sharedViewModel
        )
    }

    composable(Screen.WeeklyMedicationReport.route) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry(
                Screen.GuardianDashboard.route
            )
        }

        val sharedViewModel: MedicationViewModel =
            viewModel(parentEntry)

        WeeklyMedicationReportScreen(
            navController = navController,
            viewModel = sharedViewModel
        )
    }

    composable(Screen.AddPrescription.route) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry(
                Screen.GuardianDashboard.route
            )
        }

        val sharedViewModel: MedicationViewModel =
            viewModel(parentEntry)

        AddPrescriptionScreen(
            navController = navController,
            onScanClick = {
                // Keep the existing scanner for prescription scanning
                navController.navigate(Screen.CameraScanner.route)
            },
            viewModel = sharedViewModel
        )
    }

    // 4. UTILITY AND TRACKER SCREENS

    composable(Screen.MedicationTracker.route) {
        MedicationTrackerScreen(navController = navController)
    }

    composable(Screen.PharmacyLocator.route) {
        PharmacyLocatorScreen(navController = navController)
    }

    // Existing weekly medication scanner — unchanged

    composable(Screen.CameraScanner.route) {
        CameraScannerScreen(navController = navController)
    }

    // New expiry-date scanner — implemented separately

    composable(Screen.ExpiryDateScanner.route) {
        ExpiryDateScannerScreen(navController = navController)
    }
}