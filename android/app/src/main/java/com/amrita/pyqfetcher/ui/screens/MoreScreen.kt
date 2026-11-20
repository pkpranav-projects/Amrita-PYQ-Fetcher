package com.amrita.pyqfetcher.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrita.pyqfetcher.BuildConfig
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.core.LocalAuthenticate
import com.amrita.pyqfetcher.core.HostMode
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.data.prefs.ThemeMode
import com.amrita.pyqfetcher.ui.AppViewModel
import com.amrita.pyqfetcher.ui.LocalBottomInset
import com.amrita.pyqfetcher.ui.components.ChipRow
import com.amrita.pyqfetcher.ui.components.CoursePickerSheet
import com.amrita.pyqfetcher.ui.theme.GlassPanel

// ------------------------------------------------------------------ building blocks

/** A titled group of rows on one glass panel, like the system Settings app. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
    GlassPanel(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

// ------------------------------------------------------------------ Settings tab

@Composable
fun MoreScreen(
    app: AppViewModel,
    onBrowse: () -> Unit,
    onConnection: () -> Unit,
    onPrivacy: () -> Unit,
    vm: MoreViewModel = hiltViewModel(),
) {
    val prefs by app.prefs.collectAsStateWithLifecycle()
    val branches by app.branches.collectAsStateWithLifecycle()
    val mode by app.mode.collectAsStateWithLifecycle()
    val sync by vm.state.collectAsStateWithLifecycle()
    val p = prefs ?: return
    var pickCourse by remember { mutableStateOf(false) }
    var pickSave by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp, top = 12.dp))

        Group("Course") {
            SettingRow(Icons.Outlined.School, "${branches.firstOrNull { it.code == p.branch }?.name ?: p.branch}", "Semester ${p.semester}", onClick = { pickCourse = true }) { Chevron() }
        }

        Group("Papers") {
            SettingRow(Icons.Outlined.Download, "Save downloads to", p.saveMode.label, onClick = { pickSave = true }) { Chevron() }
            RowDivider()
            SettingRow(
                Icons.Outlined.Sync, "Check for new papers",
                when {
                    sync.running -> sync.progress?.let { "Checking… ${it.pages} pages, ${it.added} new" } ?: "Checking…"
                    p.lastSyncAt == 0L -> "Never checked"
                    else -> "Last checked ${android.text.format.DateUtils.getRelativeTimeSpanString(p.lastSyncAt)}"
                },
                onClick = if (mode == AppMode.LIVE && !sync.running) ({ vm.startSync() }) else null,
            ) { if (sync.running) TextButton(onClick = vm::stopSync) { Text("Stop") } }
            if (sync.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 18.dp))
            RowDivider()
            SettingRow(null, "Check automatically on campus", "Up to twice a day, with a notification") {
                Switch(p.autoSync, { vm.setAutoSync(it) })
            }
            RowDivider()
            SettingRow(Icons.Outlined.AccountTree, "Browse DSpace", "The university repository, folder by folder", onClick = onBrowse) { Chevron() }
        }

        Group("Connection") {
            SettingRow(Icons.Outlined.Wifi, "Campus network & VPN", "Status and how to connect", onClick = onConnection) { Chevron() }
            RowDivider()
            SettingRow(null, "Server address") {}
            Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) {
                ChipRow(HostMode.entries, p.hostMode, { when (it) { HostMode.AUTO -> "Automatic"; HostMode.DOMAIN -> "Domain"; HostMode.IP -> "Campus IP" } }) { app.setHostMode(it) }
            }
            RowDivider()
            SettingRow(null, "Demo mode", "Try the app off campus with sample PDFs") { Switch(p.demoMode, { app.setDemo(it) }) }
        }

        Group("Privacy") {
            SettingRow(Icons.Outlined.Shield, "Privacy & data", "App lock, recent apps, clear history", onClick = onPrivacy) { Chevron() }
        }

        Group("Display") {
            SettingRow(Icons.Outlined.Palette, "Theme", p.themeMode.label, onClick = { pickTheme = true }) { Chevron() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RowDivider()
                SettingRow(null, "Wallpaper colours", "Match your phone's colour palette") { Switch(p.dynamicColor, { app.setDynamic(it) }) }
            }
            RowDivider()
            SettingRow(Icons.Outlined.DarkMode, "Night reading", "Open papers with inverted colours") { Switch(p.pdfInvert, { app.setInvert(it) }) }
        }

        Group("About") {
            SettingRow(Icons.Outlined.Info, "Amrita PYQ Fetcher", "Version ${BuildConfig.VERSION_NAME} · unofficial student tool")
        }
        Spacer(Modifier.height(LocalBottomInset.current + 8.dp))
    }

    if (pickCourse) {
        CoursePickerSheet(branches, p.branch, p.semester, onDismiss = { pickCourse = false }) { b, s ->
            app.setBranch(b); app.setSemester(s); pickCourse = false
        }
    }
    if (pickSave) {
        ChoiceDialog("Save downloads to", SaveMode.entries, p.saveMode, { it.label }, { it.detail }, onDismiss = { pickSave = false }) {
            app.setSaveMode(it); pickSave = false
        }
    }
    if (pickTheme) {
        ChoiceDialog("Theme", ThemeMode.entries, p.themeMode, { it.label }, { null }, onDismiss = { pickTheme = false }) {
            app.setTheme(it); pickTheme = false
        }
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    items: List<T>,
    selected: T,
    label: (T) -> String,
    detail: (T) -> String?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = item == selected, onClick = { onPick(item) })
                        Column {
                            Text(label(item))
                            detail(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ------------------------------------------------------------------ Privacy & data

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(app: AppViewModel, onBack: () -> Unit) {
    val prefs by app.prefs.collectAsStateWithLifecycle()
    val authenticate = LocalAuthenticate.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val p = prefs ?: return
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val lockAvailable = remember {
        androidx.biometric.BiometricManager.from(context).canAuthenticate(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            },
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Privacy & data") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Group("Security") {
                SettingRow(
                    Icons.Outlined.Lock, "App lock",
                    if (lockAvailable) "Use fingerprint, face or screen lock to open the app" else "Set a screen lock on your phone first",
                ) {
                    Switch(
                        checked = p.appLock, enabled = lockAvailable,
                        onCheckedChange = { on ->
                            // Like banking apps: confirm with the lock before turning it on or off.
                            authenticate(if (on) "Turn on app lock" else "Turn off app lock") { app.setAppLock(on) }
                        },
                    )
                }
                if (p.appLock) {
                    RowDivider()
                    SettingRow(null, "Lock after") {}
                    Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) {
                        ChipRow(listOf(0, 60, 300), p.lockAfterSec, { when (it) { 0 -> "Immediately"; 60 -> "1 minute"; else -> "5 minutes" } }) { app.setLockAfter(it) }
                    }
                }
                RowDivider()
                SettingRow(
                    null, "Hide in recent apps",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Blank preview in the app switcher"
                    else "Blank preview in the app switcher (also blocks screenshots on this Android version)",
                ) { Switch(p.hideInRecents, { app.setHideInRecents(it) }) }
            }

            Group("Your data") {
                SettingRow(null, "Clear search history", onClick = { confirm = "Clear search history?" to { app.clearSearchHistory() } })
                RowDivider()
                SettingRow(null, "Clear reading history", "Continue reading and last pages", onClick = { confirm = "Clear reading history?" to { app.clearReadingHistory() } })
                RowDivider()
                SettingRow(null, "Delete notes, bookmarks and progress", onClick = { confirm = "Delete all notes, bookmarks and solved marks?" to { app.clearNotes() } })
                RowDivider()
                SettingRow(null, "Delete all downloaded papers", "Frees storage; you can download them again", onClick = { confirm = "Delete every downloaded paper from this phone?" to { app.deleteAllDownloads() } })
            }

            Group("What this app does with your data") {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Fact("No account and no sign-in.")
                    Fact("No analytics, ads or tracking. Nothing about you is sent anywhere.")
                    Fact("It only talks to Amrita's DSpace server (dspace.amritanet.edu / 172.17.18.57) to list and download papers.")
                    Fact("Papers, notes, bookmarks and history stay on this phone and are not backed up to the cloud.")
                    Fact("Files reach other apps only when you share or export them.")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    confirm?.let { (question, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(question) },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Fact(text: String) {
    Row {
        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
