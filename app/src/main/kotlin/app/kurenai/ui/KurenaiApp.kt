package app.kurenai.ui

import android.content.Intent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kurenai.feature.animator.AnimatedChapterScreen
import app.kurenai.feature.browse.BrowseScreen
import app.kurenai.feature.details.DetailsScreen
import app.kurenai.feature.library.LibraryScreen
import app.kurenai.feature.library.SearchScreen
import app.kurenai.feature.more.AboutScreen
import app.kurenai.feature.more.AiSettingsScreen
import app.kurenai.feature.more.BackupScreen
import app.kurenai.feature.more.DiagnosticsScreen
import app.kurenai.feature.more.DownloadsScreen
import app.kurenai.feature.more.HelpScreen
import app.kurenai.feature.more.MoreScreen
import app.kurenai.feature.more.SourceHealthScreen
import app.kurenai.feature.more.StatsScreen
import app.kurenai.feature.more.TasteScreen
import app.kurenai.feature.more.TrackersScreen
import app.kurenai.feature.more.WhatsNewScreen
import app.kurenai.feature.reader.ReaderScreen
import app.kurenai.feature.settings.SettingsScreen
import app.kurenai.feature.translation.TranslationScreen
import app.kurenai.feature.animator.AnimatedSettingsScreen
import app.kurenai.feature.updates.UpdatesScreen
import app.kurenai.ui.challenge.ChallengeActivity
import app.kurenai.ui.onboarding.OnboardingScreen
import app.kurenai.ui.librarian.LibrarianScreen
import app.kurenai.ui.onboarding.AppLockScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_BROWSE = "browse"
private const val ROUTE_UPDATES = "updates"
private const val ROUTE_MORE = "more"

@Composable
fun KurenaiApp(
    settingsViewModel: AppShellViewModel = hiltViewModel(),
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val challenge by settingsViewModel.challenge.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var unlocked by rememberSaveable { mutableStateOf(!settings.appLockEnabled) }

    if (!settings.disclaimerAccepted || !settings.onboardingComplete) {
        OnboardingScreen(settingsViewModel = settingsViewModel, onFinished = {})
        return
    }
    if (settings.appLockEnabled && !unlocked) {
        AppLockScreen(
            biometricEnabled = settings.biometricLockEnabled,
            onUnlocked = { unlocked = true },
            onCancel = { (context as? android.app.Activity)?.finish() },
        )
        return
    }

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val topLevel = currentRoute in setOf(ROUTE_LIBRARY, ROUTE_BROWSE, ROUTE_UPDATES, ROUTE_MORE)
    // The reader and animated player draw edge-to-edge on purpose. Every other
    // destination gets the system bars from the shell so no screen can slip under
    // the status bar. Inner Scaffolds consume the same insets instead of adding
    // a second layer.
    val immersive = currentRoute?.startsWith("reader/") == true ||
        currentRoute?.startsWith("animated/") == true

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = if (immersive) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
        bottomBar = {
            if (topLevel) {
                NavigationBar {
                    TopItem(ROUTE_LIBRARY, "Library", Icons.Rounded.Home, currentRoute, navController)
                    TopItem(ROUTE_BROWSE, "Browse", Icons.Rounded.Explore, currentRoute, navController)
                    TopItem(ROUTE_UPDATES, "Updates", Icons.Rounded.Notifications, currentRoute, navController)
                    TopItem(ROUTE_MORE, "More", Icons.Rounded.MoreHoriz, currentRoute, navController)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LIBRARY,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    onMangaClick = { manga -> navController.navigate("details/${manga.id}") },
                    onContinueReading = { item ->
                        item.chapter?.let { navController.navigate("reader/${item.manga.id}/${it.id}") }
                    },
                    onOpenSearch = { navController.navigate("search") },
                    onOpenTaste = { navController.navigate("taste") },
                    onOpenStats = { navController.navigate("stats") },
                )
            }
            composable(ROUTE_BROWSE) {
                BrowseScreen(
                    onOpenManga = { id -> navController.navigate("details/$id") },
                    onOpenSourceHealth = { navController.navigate("sources") },
                )
            }
            composable(ROUTE_UPDATES) {
                UpdatesScreen(
                    onOpenManga = { id -> navController.navigate("details/$id") },
                    onRead = { mangaId, chapterId -> navController.navigate("reader/$mangaId/$chapterId") },
                )
            }
            composable(ROUTE_MORE) {
                MoreScreen(
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenSources = { navController.navigate("sources") },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenTaste = { navController.navigate("taste") },
                    onOpenTrackers = { navController.navigate("trackers") },
                    onOpenBackup = { navController.navigate("backup") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    onOpenHelp = { navController.navigate("help") },
                    onOpenWhatsNew = { navController.navigate("whatsnew") },
                    onOpenAbout = { navController.navigate("about") },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("details/{mangaId}", arguments = listOf(navArgument("mangaId") { type = NavType.StringType })) {
                DetailsScreen(
                    onBack = { navController.popBackStack() },
                    onRead = { mangaId, chapterId -> navController.navigate("reader/$mangaId/$chapterId") },
                    onOpenManga = { id -> navController.navigate("details/$id") },
                )
            }
            composable(
                "reader/{mangaId}/{chapterId}",
                arguments = listOf(
                    navArgument("mangaId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType },
                ),
            ) {
                ReaderScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAnimated = { mangaId, chapterId -> navController.navigate("animated/$mangaId/$chapterId") },
                )
            }
            composable("animated/{mangaId}/{chapterId}", arguments = listOf(
                navArgument("mangaId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType },
            )) {
                AnimatedChapterScreen(onBack = { navController.popBackStack() })
            }
            composable("search") {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenManga = { id -> navController.navigate("details/$id") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    onOpenSources = { navController.navigate("sources") },
                    onOpenAnimated = { navController.navigate("animated-settings") },
                    onOpenTranslation = { navController.navigate("translation") },
                    onOpenBackup = { navController.navigate("backup") },
                    onOpenDiagnostics = { navController.navigate("diagnostics") },
                    onOpenDownloads = { navController.navigate("downloads") },
                    onOpenTrackers = { navController.navigate("trackers") },
                    onOpenAbout = { navController.navigate("about") },
                    onOpenHelp = { navController.navigate("help") },
                    onOpenWhatsNew = { navController.navigate("whatsnew") },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenTaste = { navController.navigate("taste") },
                    onOpenAiSettings = { navController.navigate("ai") },
                )
            }
            composable("sources") { SourceHealthScreen(onBack = { navController.popBackStack() }) }
            composable("downloads") { DownloadsScreen(onBack = { navController.popBackStack() }) }
            composable("stats") { StatsScreen(onBack = { navController.popBackStack() }) }
            composable("taste") { TasteScreen(onBack = { navController.popBackStack() }) }
            composable("trackers") { TrackersScreen(onBack = { navController.popBackStack() }) }
            composable("backup") { BackupScreen(onBack = { navController.popBackStack() }) }
            composable("diagnostics") { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
            composable("help") { HelpScreen(onBack = { navController.popBackStack() }) }
            composable("whatsnew") { WhatsNewScreen(onBack = { navController.popBackStack() }) }
            composable("about") { AboutScreen(onBack = { navController.popBackStack() }) }
            composable("ai") { AiSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("translation") { TranslationScreen(onBack = { navController.popBackStack() }) }
            composable("animated-settings") { AnimatedSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("librarian") {
                LibrarianScreen(
                    onBack = { navController.popBackStack() },
                    onOpenManga = { id -> navController.navigate("details/$id") },
                )
            }
        }
    }

    challenge?.let { pending ->
        AlertDialog(
            onDismissRequest = { settingsViewModel.cancelChallenge() },
            title = { Text("One quick check") },
            text = { Text("${pending.host} needs you to complete a browser verification. Kurenai will return to what it was doing automatically.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, ChallengeActivity::class.java)
                            .putExtra("challengeId", pending.id)
                            .putExtra("url", pending.url)
                            .putExtra("host", pending.host)
                            .putExtra("userAgent", pending.userAgent),
                    )
                }) { Text("Open verification") }
            },
            dismissButton = { TextButton(onClick = { settingsViewModel.cancelChallenge() }) { Text("Not now") } },
        )
    }
}

@Composable
private fun RowScope.TopItem(
    route: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    currentRoute: String?,
    navController: androidx.navigation.NavController,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, label) },
        label = { Text(label) },
    )
}
