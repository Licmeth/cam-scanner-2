package com.licmeth.camscanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.licmeth.camscanner.ui.screen.DocumentPreviewScreen
import com.licmeth.camscanner.ui.screen.MainScreen
import com.licmeth.camscanner.ui.screen.SettingsScreen

@Composable
fun CamScannerNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Main.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPreview = { imagePath ->
                    navController.navigate(Screen.DocumentPreview.createRoute(imagePath))
                }
            )
        }

        composable(
            route = Screen.DocumentPreview.route,
            arguments = listOf(navArgument("imagePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            DocumentPreviewScreen(
                imagePath = imagePath,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
