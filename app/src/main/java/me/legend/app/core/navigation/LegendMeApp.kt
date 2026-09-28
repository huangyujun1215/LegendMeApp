package me.legend.app.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.legend.app.feature.creation.CreationScreen
import me.legend.app.feature.recording.RecordingRoute
import me.legend.app.feature.settings.SettingsRoute
import me.legend.app.feature.timeline.TimelineScreen

private data class Destination(val route: String, val label: String, val shortLabel: String)

private val destinations = listOf(
    Destination("recording", "记录", "记"),
    Destination("timeline", "时间线", "时"),
    Destination("creation", "创作", "作"),
    Destination("settings", "设置", "设"),
)

@Composable
fun LegendMeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.shortLabel) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "recording",
            modifier = Modifier.padding(padding),
        ) {
            composable("recording") { RecordingRoute() }
            composable("timeline") { TimelineScreen() }
            composable("creation") { CreationScreen() }
            composable("settings") { SettingsRoute() }
        }
    }
}
