package com.example.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.AnimeRepository
import com.example.ui.screens.detail.DetailScreen
import com.example.ui.screens.detail.DetailViewModel
import com.example.ui.screens.favorites.FavoritesHistoryScreen
import com.example.ui.screens.favorites.FavoritesHistoryViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.player.PlayerViewModel
import com.example.ui.screens.schedule.ScheduleScreen
import com.example.ui.screens.schedule.ScheduleViewModel
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.search.SearchViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.ZenimePrimary

sealed class Screen(
    val route: String,
    val title: String? = null,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Splash : Screen("splash")
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Search : Screen("search", "Cari", Icons.Filled.Search, Icons.Outlined.Search)
    data object Schedule : Screen("schedule", "Jadwal", Icons.Filled.DateRange, Icons.Outlined.DateRange)
    data object Favorites : Screen("favorites", "Koleksi", Icons.Filled.Bookmark, Icons.Outlined.Bookmark)
    data object Settings : Screen("settings", "Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings)

    data object Detail : Screen("detail/{animeId}") {
        fun createRoute(animeId: String) = "detail/$animeId"
    }

    data object Player : Screen("player/{episodeId}/{animeId}") {
        fun createRoute(episodeId: String, animeId: String) = "player/$episodeId/$animeId"
    }
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Schedule,
    Screen.Favorites,
    Screen.Settings
)

@Composable
fun ZenimeAppNavHost(
    repository: AnimeRepository,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.fillMaxSize()
        ) {
            // Splash Screen
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // Home Screen
            composable(Screen.Home.route) {
                val homeViewModel = remember { HomeViewModel(repository) }
                HomeScreen(
                    viewModel = homeViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            // Search Screen
            composable(Screen.Search.route) {
                val searchViewModel = remember { SearchViewModel(repository) }
                SearchScreen(
                    viewModel = searchViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    }
                )
            }

            // Schedule Screen
            composable(Screen.Schedule.route) {
                val scheduleViewModel = remember { ScheduleViewModel(repository) }
                ScheduleScreen(
                    viewModel = scheduleViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    }
                )
            }

            // Favorites & Watch History Screen
            composable(Screen.Favorites.route) {
                val favViewModel = remember { FavoritesHistoryViewModel(repository) }
                FavoritesHistoryScreen(
                    viewModel = favViewModel,
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                    onPlayEpisodeClick = { episodeId, animeId ->
                        navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                    }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                val settingsViewModel = remember { SettingsViewModel(repository) }
                SettingsScreen(
                    viewModel = settingsViewModel
                )
            }

            // Detail Screen
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("animeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
                val detailViewModel = remember(animeId) { DetailViewModel(repository, animeId) }
                DetailScreen(
                    viewModel = detailViewModel,
                    onBackClick = { navController.popBackStack() },
                    onEpisodeClick = { episodeId, _ ->
                        navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                    }
                )
            }

            // Video Player Screen
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("episodeId") { type = NavType.StringType },
                    navArgument("animeId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""
                val animeId = backStackEntry.arguments?.getString("animeId") ?: ""
                val playerViewModel = remember(episodeId, animeId) { PlayerViewModel(repository, episodeId, animeId) }

                PlayerScreen(
                    viewModel = playerViewModel,
                    onBackClick = { navController.popBackStack() },
                    onNextEpisodeClick = { nextEpId ->
                        navController.navigate(Screen.Player.createRoute(nextEpId, animeId)) {
                            popUpTo(Screen.Player.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        if (showBottomBar) {
            FloatingPillBottomBar(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
fun FloatingPillBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            color = ZenimePrimary, // Solid Crimson Red
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavScreens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val icon = if (selected) screen.selectedIcon!! else screen.unselectedIcon!!

                    IconButton(
                        onClick = { onNavigate(screen) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = if (selected) Color.White.copy(alpha = 0.28f) else Color.Transparent,
                                shape = CircleShape
                            )
                            .then(
                                if (selected) Modifier.border(1.2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                else Modifier
                            )
                            .testTag("nav_item_${screen.route}")
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = screen.title,
                            tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
