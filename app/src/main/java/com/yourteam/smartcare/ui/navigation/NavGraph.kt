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

private fun NavGraphBuilder.setupSmartCareRoutes(navController: NavHostController) {

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
            onTakeMedicineClick = { navController.navigate(Screen.MedicationTracker.route) },
            onPharmacyClick = { navController.navigate(Screen.PharmacyLocator.route) },
            onScanLabelClick = { navController.navigate(Screen.CameraScanner.route) },
            onLogoutClick = {
                scope.launch {
                    SupabaseClient.client.auth.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.PatientHome.route) { inclusive = true }
                    }
                }
            }
        )
    }

    // 3. GUARDIAN FLOW
    composable(Screen.GuardianSetup.route) {
        GuardianSetupScreen(navController = navController)
    }

    // Shared ViewModel Scope for Guardian Dashboard & Add Prescription
    composable(Screen.GuardianDashboard.route) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry(Screen.GuardianDashboard.route)
        }
        val sharedViewModel: MedicationViewModel = viewModel(parentEntry)

        GuardianDashboardScreen(navController = navController, viewModel = sharedViewModel)
    }

    composable(Screen.AddPrescription.route) { backStackEntry ->
        val parentEntry = remember(backStackEntry) {
            navController.getBackStackEntry(Screen.GuardianDashboard.route)
        }
        val sharedViewModel: MedicationViewModel = viewModel(parentEntry)

        AddPrescriptionScreen(
            navController = navController,
            onScanClick = { navController.navigate(Screen.CameraScanner.route) },
            viewModel = sharedViewModel
        )
    }

    // 4. UTILITY & TRACKER SCREENS
    composable(Screen.MedicationTracker.route) {
        MedicationTrackerScreen(navController = navController)
    }

    composable(Screen.PharmacyLocator.route) {
        PharmacyLocatorScreen(navController = navController)
    }

    composable(Screen.CameraScanner.route) {
        CameraScannerScreen(navController = navController)
    }
}