package com.example.mobileaudiowhatsapp

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.mobileaudiowhatsapp.ui.CallDetailsScreen
import com.example.mobileaudiowhatsapp.ui.DashboardScreen
import com.example.mobileaudiowhatsapp.ui.HistoryScreen
import com.example.mobileaudiowhatsapp.ui.SettingsScreen
import com.example.mobileaudiowhatsapp.ui.historyViewModel

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Dashboard) {
        composable<Dashboard> {
            DashboardScreen(navController)
        }
        composable<History> { backStackEntry ->
            HistoryScreen(navController, viewModel = historyViewModel(navController, backStackEntry))
        }
        composable<CallDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<CallDetails>()
            CallDetailsScreen(navController = navController, callId = route.id)
        }
        composable<Settings> {
            SettingsScreen(navController)
        }
    }
}
