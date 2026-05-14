package com.healthdashboard.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthdashboard.mobile.data.AuthRepository
import com.healthdashboard.mobile.data.HealthRepository
import com.healthdashboard.mobile.ui.ChartScreen
import com.healthdashboard.mobile.ui.LoginScreen
import com.healthdashboard.mobile.ui.RecordsScreen
import com.healthdashboard.mobile.ui.theme.HealthDashboardTheme

@Composable
fun HealthDashboardApp() {
    val context = LocalContext.current.applicationContext
    val authRepository = remember(context) { AuthRepository(context) }
    val healthRepository = remember(authRepository) { HealthRepository(authRepository) }
    val sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(authRepository))
    val sessionState by sessionViewModel.state.collectAsState()
    val navController = rememberNavController()

    HealthDashboardTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (!sessionState.isAuthenticated) {
                LoginScreen(
                    state = sessionState,
                    onLogin = { password, apiBaseUrl -> sessionViewModel.login(password, apiBaseUrl) },
                )
            } else {
                val items = listOf(Screen.Records, Screen.Charts)
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { screen ->
                                NavigationBarItem(
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    label = { Text(screen.label) },
                                    icon = { Text(screen.icon) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Records.route,
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable(Screen.Records.route) {
                            RecordsScreen(
                                repository = healthRepository,
                                onLogout = { sessionViewModel.logout() },
                            )
                        }
                        composable(Screen.Charts.route) {
                            ChartScreen(
                                repository = healthRepository,
                                onLogout = { sessionViewModel.logout() },
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: String) {
    data object Records : Screen("records", "记录", "记")
    data object Charts : Screen("charts", "图表", "图")
}
