package com.licmeth.camscanner.ui.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object DocumentPreview : Screen("document_preview/{imagePath}") {
        fun createRoute(imagePath: String) = "document_preview/$imagePath"
    }
    object Settings : Screen("settings")
}
