package com.focusguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.focusguard.data.local.SessionDataStore
import com.focusguard.data.repository.UsageRepositoryImpl
import com.focusguard.domain.usecase.GetDailyUsageUseCase
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.toArgb
import com.focusguard.ui.blocks.BlocksScreen
import com.focusguard.ui.blocks.BlocksViewModel
import com.focusguard.ui.home.HomeScreen
import com.focusguard.ui.home.HomeViewModel
import com.focusguard.ui.sessions.SessionsScreen
import com.focusguard.ui.settings.SettingsScreen
import com.focusguard.ui.theme.FGColors
import com.focusguard.ui.theme.FG_Error
import com.focusguard.ui.theme.FG_Seg0
import com.focusguard.ui.theme.FG_Seg1
import com.focusguard.ui.theme.FG_Seg2
import com.focusguard.ui.theme.FG_Seg3
import com.focusguard.ui.theme.FG_SegOther
import com.focusguard.ui.theme.FG_Track
import com.focusguard.ui.theme.FG_TrackDark
import com.focusguard.ui.theme.FG_TxtSecDark
import com.focusguard.ui.theme.FG_TxtSecLight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.NavigationBarItemDefaults

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",     Icons.Filled.Home)
    object Blocks   : Screen("blocks",   "Blocks",   Icons.Filled.Block)
    object Sessions : Screen("sessions", "Sessions", Icons.Filled.Timer)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ViewModels outlive this Activity across rotation, so everything they reference
        // must be built from the application context, never from `this`.
        val appContext   = applicationContext
        val dataSource   = UsageStatsDataSource(appContext)
        val repository   = UsageRepositoryImpl(appContext, dataSource)
        val useCase      = GetDailyUsageUseCase(repository)
        val limitManager = LimitManager(appContext)
        val sessionDataStore = SessionDataStore(appContext)
        val sessionScheduler = com.focusguard.core.scheduling.SessionScheduler(appContext)

        // Obtained through the ViewModelStore so they survive configuration changes and
        // their viewModelScope is cancelled when the Activity is finished for good.
        // The initializer only runs the first time; on recreation the existing instance is reused.
        val homeViewModel = ViewModelProvider(this, viewModelFactory {
            initializer { HomeViewModel(useCase, repository) }
        })[HomeViewModel::class.java]
        val blocksViewModel = ViewModelProvider(this, viewModelFactory {
            initializer { BlocksViewModel(useCase, limitManager) }
        })[BlocksViewModel::class.java]
        val sessionsViewModel = ViewModelProvider(this, viewModelFactory {
            initializer { com.focusguard.ui.sessions.SessionsViewModel(sessionDataStore, sessionScheduler) }
        })[com.focusguard.ui.sessions.SessionsViewModel::class.java]

        val settingsViewModel = ViewModelProvider(
            this, com.focusguard.ui.settings.SettingsViewModelFactory(appContext)
        )[com.focusguard.ui.settings.SettingsViewModel::class.java]

        val startRoute = intent.getStringExtra("navigate_to") ?: Screen.Home.route

        setContent {
            FocusGuardApp(
                homeViewModel     = homeViewModel,
                blocksViewModel   = blocksViewModel,
                sessionsViewModel = sessionsViewModel,
                settingsViewModel = settingsViewModel,
                startRoute        = startRoute
            )
        }
    }

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
    val navController   = rememberNavController()
    val settingsState   by settingsViewModel.state.collectAsState()
    val systemDarkMode  = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMode      = when (settingsState.themeMode) {
        com.focusguard.ui.settings.ThemeMode.LIGHT -> false
        com.focusguard.ui.settings.ThemeMode.DARK -> true
        com.focusguard.ui.settings.ThemeMode.SYSTEM -> systemDarkMode
    }

    LaunchedEffect(startRoute) {
        if (startRoute != Screen.Home.route) {
            navController.navigate(startRoute) {
                popUpTo(Screen.Home.route)
                launchSingleTop = true
            }
        }
    }

    val items = listOf(Screen.Home, Screen.Blocks, Screen.Sessions, Screen.Settings)

    val bg       = FGColors.header(isDarkMode) // using header for bottom bar / status bar to match TopAppBar
    val card     = FGColors.card(isDarkMode)
    val surf     = FGColors.surf(isDarkMode)
    val txtPri   = FGColors.txtPri(isDarkMode)
    val txtSec   = FGColors.txtSec(isDarkMode)

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDarkMode
            controller.isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    val colorScheme = if (isDarkMode) {
        androidx.compose.material3.darkColorScheme(
            primary = FG_Seg3, onPrimary = FG_Seg0,
            primaryContainer = FG_Seg1, onPrimaryContainer = Color.White,
            secondary = FG_Seg2,
            secondaryContainer = FG_TrackDark, onSecondaryContainer = FG_SegOther,
            tertiary = FG_Seg3, tertiaryContainer = FG_Seg1, onTertiaryContainer = Color.White,
            outline = FG_TxtSecDark, outlineVariant = FG_TrackDark,
            background = FGColors.bg(isDarkMode), onBackground = txtPri,
            surface = surf, onSurface = txtPri,
            surfaceVariant = card, onSurfaceVariant = txtSec,
            error = FG_Error
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = FG_Seg0, onPrimary = Color.White,
            primaryContainer = FG_Seg1, onPrimaryContainer = Color.White,
            secondary = FG_Seg2,
            secondaryContainer = FG_Track, onSecondaryContainer = FG_Seg0,
            tertiary = FG_Seg3, tertiaryContainer = FG_Track, onTertiaryContainer = FG_Seg0,
            outline = FG_TxtSecLight, outlineVariant = FG_SegOther,
            background = FGColors.bg(isDarkMode), onBackground = txtPri,
            surface = surf, onSurface = txtPri,
            surfaceVariant = card, onSurfaceVariant = txtSec,
            error = FG_Error
        )
    }

    androidx.compose.material3.MaterialTheme(
        colorScheme = colorScheme
    ) {
        Scaffold(
            // Each screen's own top bar handles the status-bar inset; the bottom bar handles the navigation bar
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(
                    containerColor = bg,
                    contentColor = txtSec
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon     = { Icon(screen.icon, contentDescription = screen.title) },
                            label    = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            colors   = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (isDarkMode) FG_SegOther else FG_Seg0,
                                selectedTextColor = if (isDarkMode) FG_SegOther else FG_Seg0,
                                indicatorColor    = FG_Seg1.copy(alpha = 0.2f),
                                unselectedIconColor = txtSec,
                                unselectedTextColor = txtSec
                            ),
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = navController,
                startDestination = Screen.Home.route,
                modifier         = Modifier.padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
                enterTransition  = { slideInHorizontally(initialOffsetX = { 50 }) + fadeIn() },
                exitTransition   = { slideOutHorizontally(targetOffsetX = { -50 }) + fadeOut() },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -50 }) + fadeIn() },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { 50 }) + fadeOut() }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel     = homeViewModel,
                        isDarkMode    = isDarkMode,
                        onThemeToggle = { /* handled in settings */ }
                    )
                }
                composable(Screen.Blocks.route)   { BlocksScreen(blocksViewModel, isDarkMode) }
                composable(Screen.Sessions.route) { SessionsScreen(sessionsViewModel, isDarkMode) }
                composable(Screen.Settings.route) { 
                    SettingsScreen(
                        viewModel = settingsViewModel, 
                        isDarkMode = isDarkMode,
                        onNavigateToPrivacy = { navController.navigate("privacy") }
                    ) 
                }
                composable("privacy") { 
                    com.focusguard.ui.settings.PrivacyScreen(
                        onBack = { navController.popBackStack() },
                        isDarkMode = isDarkMode
                    ) 
                }
            }
        }
    }
}