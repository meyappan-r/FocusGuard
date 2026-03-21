package com.focusguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusguard.data.datasource.UsageStatsDataSource
import com.focusguard.data.local.LimitManager
import com.focusguard.data.repository.UsageRepositoryImpl
import com.focusguard.domain.usecase.GetDailyUsageUseCase
import com.focusguard.ui.blocks.BlocksScreen
import com.focusguard.ui.blocks.BlocksViewModel
import com.focusguard.ui.home.HomeScreen
import com.focusguard.ui.home.HomeViewModel
import com.focusguard.ui.sessions.SessionsScreen
import com.focusguard.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Blocks : Screen("blocks", "Blocks", Icons.Filled.Block)
    object Sessions : Screen("sessions", "Sessions", Icons.Filled.Timer)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataSource = UsageStatsDataSource(this)
        val repository = UsageRepositoryImpl(this, dataSource)
        val useCase = GetDailyUsageUseCase(repository)
        val limitManager = LimitManager(this)
        
        val homeViewModel = HomeViewModel(useCase)
        val blocksViewModel = BlocksViewModel(useCase, limitManager)
        
        val sessionStorage = com.focusguard.data.local.SessionStorage(this)
        val sessionsViewModel = com.focusguard.ui.sessions.SessionsViewModel(sessionStorage)
        
        val settingsViewModel = androidx.lifecycle.ViewModelProvider(
            this, com.focusguard.ui.settings.SettingsViewModelFactory(this)
        )[com.focusguard.ui.settings.SettingsViewModel::class.java]

        val startRoute = intent.getStringExtra("navigate_to") ?: Screen.Home.route

        setContent {
            FocusGuardApp(homeViewModel, blocksViewModel, sessionsViewModel, settingsViewModel, startRoute)
        }
    }

    // Called when the app is already open & BlockActivity sends a new intent
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun FocusGuardApp(
    homeViewModel: HomeViewModel, 
    blocksViewModel: BlocksViewModel, 
    sessionsViewModel: com.focusguard.ui.sessions.SessionsViewModel,
    settingsViewModel: com.focusguard.ui.settings.SettingsViewModel,
    startRoute: String = Screen.Home.route
) {
    val navController = rememberNavController()
    val settingsState by settingsViewModel.state.collectAsState()

    // Navigate immediately to startRoute if it's not Home (e.g. coming from BlockActivity)
    LaunchedEffect(startRoute) {
        if (startRoute != Screen.Home.route) {
            navController.navigate(startRoute) {
                popUpTo(Screen.Home.route)
                launchSingleTop = true
            }
        }
    }
    val items = listOf(
        Screen.Home,
        Screen.Blocks,
        Screen.Sessions,
        Screen.Settings
    )

    androidx.compose.material3.MaterialTheme(
        colorScheme = if (settingsState.isDarkMode) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
                composable(Screen.Home.route) { HomeScreen(homeViewModel) }
                composable(Screen.Blocks.route) { BlocksScreen(blocksViewModel) }
                composable(Screen.Sessions.route) { SessionsScreen(sessionsViewModel) }
                composable(Screen.Settings.route) { com.focusguard.ui.settings.SettingsScreen(settingsViewModel) }
            }
        }
    }
}