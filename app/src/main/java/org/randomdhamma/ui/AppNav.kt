package org.randomdhamma.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.randomdhamma.appContainer
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.Pick
import org.randomdhamma.data.PickSource
import org.randomdhamma.util.suspendRunCatching

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SUTTA = "sutta?url={url}"
    const val TALK = "talk?url={url}&title={title}"

    fun sutta(url: String) = "sutta?url=${Uri.encode(url)}"
    fun talk(url: String?, title: String?) =
        "talk?url=${Uri.encode(url.orEmpty())}&title=${Uri.encode(title.orEmpty())}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Today", Icons.Filled.Home),
    Tab(Routes.HISTORY, "History", Icons.Filled.History),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

fun NavHostController.openPick(pick: Pick) = when (pick.type) {
    ContentType.SUTTA -> navigate(Routes.sutta(pick.url))
    ContentType.TALK -> navigate(Routes.talk(pick.url, pick.title))
}

@Composable
fun AppNav(pendingIntent: Intent?, onIntentHandled: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val container = context.appContainer
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    RequestNotificationPermission()

    // Deep links from notifications / the media session.
    LaunchedEffect(pendingIntent) {
        val intent = pendingIntent ?: return@LaunchedEffect
        // On a cold start this can run before NavHost (inside the Scaffold) has set the graph;
        // navigating then would crash. The flow only emits once the graph has a destination.
        nav.currentBackStackEntryFlow.first()
        val url = intent.getStringExtra(NavIntents.EXTRA_URL)
        val title = intent.getStringExtra(NavIntents.EXTRA_TITLE)
        when (intent.getStringExtra(NavIntents.EXTRA_TARGET)) {
            NavIntents.TARGET_SUTTA -> url?.let { nav.navigate(Routes.sutta(it)) }
            NavIntents.TARGET_TALK -> url?.let { nav.navigate(Routes.talk(it, title)) }
            NavIntents.TARGET_PLAYER -> nav.navigate(Routes.talk(null, null))
            NavIntents.TARGET_RESOLVE -> scope.launch {
                suspendRunCatching { container.content.pick(PickSource.DAILY) }
                    .onSuccess { nav.openPick(it) }
                    .onFailure { snackbar.showSnackbar("Couldn't reach dhammatalks.org. Check your connection.") }
            }
        }
        onIntentHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    // Each screen handles its own top inset (tab screens pad for the status bar, detail
    // screens use a TopAppBar), so the outer Scaffold must not add system-bar padding.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(onOpen = nav::openPick, snackbar = snackbar) }
            composable(Routes.HISTORY) { HistoryScreen(onOpen = nav::openPick) }
            composable(Routes.SETTINGS) { SettingsScreen(snackbar = snackbar) }
            composable(
                Routes.SUTTA,
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { entry ->
                SuttaScreen(
                    initialUrl = entry.arguments?.getString("url").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                Routes.TALK,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                PlayerScreen(
                    url = entry.arguments?.getString("url").orEmpty().ifBlank { null },
                    title = entry.arguments?.getString("title").orEmpty().ifBlank { null },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
