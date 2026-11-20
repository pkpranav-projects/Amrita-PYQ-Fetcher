package com.amrita.pyqfetcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amrita.pyqfetcher.core.AppLock
import com.amrita.pyqfetcher.core.LocalAuthenticate
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.data.prefs.ThemeMode
import com.amrita.pyqfetcher.ui.components.ConnectionSheet
import com.amrita.pyqfetcher.ui.components.QueueBanner
import com.amrita.pyqfetcher.ui.components.QueueSheet
import com.amrita.pyqfetcher.ui.reader.ReaderScreen
import com.amrita.pyqfetcher.ui.screens.BrowseScreen
import com.amrita.pyqfetcher.ui.screens.HomeScreen
import com.amrita.pyqfetcher.ui.screens.LibraryScreen
import com.amrita.pyqfetcher.ui.screens.MoreScreen
import com.amrita.pyqfetcher.ui.screens.OnboardingScreen
import com.amrita.pyqfetcher.ui.screens.PrivacyScreen
import com.amrita.pyqfetcher.ui.screens.SearchScreen
import com.amrita.pyqfetcher.ui.screens.SubjectScreen
import com.amrita.pyqfetcher.ui.theme.AmritaTheme
import com.amrita.pyqfetcher.ui.theme.GlassBackground
import com.amrita.pyqfetcher.ui.theme.LocalGlass
import com.amrita.pyqfetcher.ui.theme.glassBorder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

object Routes {
    const val ONBOARD = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val MORE = "more"
    const val SEARCH = "search"
    const val BROWSE = "browse"
    const val PRIVACY = "privacy"
    const val SUBJECT = "subject/{subjectId}"
    const val READER = "reader/{paperId}?page={page}&timer={timer}"

    fun subject(id: String) = "subject/${Uri.encode(id)}"
    fun reader(paperId: String, page: Int = -1, timer: Int = 0) = "reader/$paperId?page=$page&timer=$timer"
}

/** Space tab screens leave at the bottom so content can scroll under the floating glass bar. */
val LocalBottomInset = compositionLocalOf { 0.dp }

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.LIBRARY, "Library", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Filled.LibraryBooks),
    Tab(Routes.MORE, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
fun AppRoot(app: AppViewModel = hiltViewModel(), messages: Messages, appLock: AppLock) {
    val prefs by app.prefs.collectAsStateWithLifecycle()
    val locked by appLock.locked.collectAsStateWithLifecycle()
    val p = prefs
    AmritaTheme(mode = p?.themeMode ?: ThemeMode.DARK, dynamicColor = p?.dynamicColor ?: false) {
        GlassBackground {
            when {
                p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> AppContent(app, messages, startOnboarding = !p.onboarded)
            }
            // Lock screen covers everything (including sheets) until the user authenticates.
            if (p != null && p.appLock && locked) LockScreen(onUnlock = appLock::unlocked)
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val authenticate = LocalAuthenticate.current
    LaunchedEffect(Unit) { authenticate("Unlock Amrita PYQ", onUnlock) }
    GlassBackground(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {}) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Amrita PYQ is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { authenticate("Unlock Amrita PYQ", onUnlock) }) { Text("Unlock") }
        }
    }
}

@Composable
private fun AppContent(app: AppViewModel, messages: Messages, startOnboarding: Boolean) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val queue by app.queue.collectAsStateWithLifecycle()
    val progress by app.progress.collectAsStateWithLifecycle()
    val prefs by app.prefs.collectAsStateWithLifecycle()
    val mode by app.mode.collectAsStateWithLifecycle()
    val conn by app.conn.collectAsStateWithLifecycle()
    var showQueue by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    val haze = rememberHazeState()

    LaunchedEffect(Unit) {
        messages.flow.collect { m ->
            val r = snackbar.showSnackbar(
                m.text, actionLabel = m.action,
                duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            m.onResult(r == SnackbarResult.ActionPerformed)
        }
    }

    // Like most apps, ask for notification permission the first time it's useful: a download.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(queue.visible) {
        val asked = prefs?.notifAsked ?: true
        if (queue.visible && !asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            app.markNotifAsked()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val isTab = tabs.any { it.route == route }
    val openConnection = { showConnection = true }
    val toReader: (String, Int) -> Unit = { id, page -> nav.navigate(Routes.reader(id, page)) }
    val toSubject: (String) -> Unit = { nav.navigate(Routes.subject(it)) }
    val bottomInset: Dp = if (isTab) (if (queue.visible) 168.dp else 104.dp) else 0.dp

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) Routes.ONBOARD else Routes.HOME,
                modifier = Modifier.fillMaxSize().hazeSource(haze).then(if (isTab) Modifier.statusBarsPadding() else Modifier),
                enterTransition = { fadeIn() + slideInHorizontally { it / 14 } },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 14 } },
            ) {
                composable(Routes.ONBOARD) {
                    OnboardingScreen(app) { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARD) { inclusive = true } } }
                }
                composable(Routes.HOME) {
                    HomeScreen(app, onSearch = { nav.navigate(Routes.SEARCH) }, onSubject = toSubject, onReader = toReader, onConnection = openConnection)
                }
                composable(Routes.LIBRARY) { LibraryScreen(onReader = toReader) }
                composable(Routes.MORE) {
                    MoreScreen(
                        app, onBrowse = { nav.navigate(Routes.BROWSE) }, onConnection = openConnection,
                        onPrivacy = { nav.navigate(Routes.PRIVACY) },
                    )
                }
                composable(Routes.SEARCH) { SearchScreen(onBack = { nav.popBackStack() }, onReader = toReader) }
                composable(Routes.BROWSE) { BrowseScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PRIVACY) { PrivacyScreen(app, onBack = { nav.popBackStack() }) }
                composable(Routes.SUBJECT, arguments = listOf(navArgument("subjectId") { type = NavType.StringType })) {
                    SubjectScreen(
                        defaultMode = prefs?.saveMode ?: SaveMode.APP_PRIVATE,
                        onBack = { nav.popBackStack() },
                        onReader = { id, page, timer -> nav.navigate(Routes.reader(id, page, timer)) },
                    )
                }
                composable(
                    Routes.READER,
                    arguments = listOf(
                        navArgument("paperId") { type = NavType.StringType },
                        navArgument("page") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("timer") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) {
                    ReaderScreen(
                        onBack = { nav.popBackStack() },
                        onOpenPaper = { id -> nav.navigate(Routes.reader(id)) { popUpTo(Routes.READER) { inclusive = true } } },
                    )
                }
            }
        }

        // Floating frosted bottom area: download progress + navigation.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnackbarHost(snackbar)
            AnimatedVisibility(isTab && queue.visible) {
                GlassBar(haze, RoundedCornerShape(20.dp)) {
                    QueueBanner(queue) {
                        if (queue.pending == 0 && queue.failed == 0) app.clearFinishedQueue() else showQueue = true
                    }
                }
            }
            AnimatedVisibility(isTab) {
                GlassBar(haze, CircleShape) {
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        tabs.forEach { t -> NavItem(t, route == t.route, Modifier.weight(1f)) { navigateTab(nav, t.route) } }
                    }
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            queue, progress, onDismiss = { showQueue = false },
            onPause = app::pauseQueue, onResume = { app.resumeQueue() }, onRetry = { app.retryFailed() },
            onCancel = { app.cancelQueue(); showQueue = false }, onClear = { app.clearFinishedQueue() },
        )
    }
    val currentPrefs = prefs
    if (showConnection && currentPrefs != null) {
        ConnectionSheet(
            mode, conn, currentPrefs.demoMode,
            onDismiss = { showConnection = false },
            onRecheck = app::refreshConnection,
            onDemo = { app.setDemo(it) },
        )
    }
}

/** A bar with real backdrop blur (Android 12+; a tinted scrim on older phones). */
@Composable
private fun GlassBar(haze: HazeState, shape: androidx.compose.ui.graphics.Shape, content: @Composable () -> Unit) {
    val g = LocalGlass.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .hazeEffect(state = haze, style = HazeStyle(backgroundColor = g.backdrop, tints = listOf(HazeTint(g.barTint)), blurRadius = 24.dp))
            .border(glassBorder(g), shape),
    ) { content() }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.clip(CircleShape)
                .then(if (selected) Modifier.background(c.primary.copy(alpha = 0.16f)) else Modifier)
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Icon(if (selected) tab.selectedIcon else tab.icon, tab.label, tint = if (selected) c.primary else c.onSurfaceVariant)
        }
        Spacer(Modifier.width(0.dp).height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (selected) c.primary else c.onSurfaceVariant)
    }
}

private fun navigateTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
