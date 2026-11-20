package com.amrita.pyqfetcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.core.Config
import com.amrita.pyqfetcher.core.ConnState
import com.amrita.pyqfetcher.core.ConnStatus
import com.amrita.pyqfetcher.data.db.BranchEntity
import com.amrita.pyqfetcher.data.db.PaperRow
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.domain.ExamFilter
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.domain.Packs
import com.amrita.pyqfetcher.ui.QueueSummary
import com.amrita.pyqfetcher.ui.theme.Amber
import com.amrita.pyqfetcher.ui.theme.Emerald

// ------------------------------------------------------------------ connection

/** Small status pill: green = campus live, amber = demo / offline. */
@Composable
fun ConnectionPill(mode: AppMode, conn: ConnState, onClick: () -> Unit) {
    val checking = conn.status == ConnStatus.CHECKING && mode != AppMode.LIVE
    val (color, label) = when {
        mode == AppMode.LIVE -> Emerald to "Campus"
        mode == AppMode.DEMO -> Amber to "Demo"
        checking -> MaterialTheme.colorScheme.onSurfaceVariant to "Checking"
        else -> Amber to "Offline"
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (checking) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = color)
            else Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One-line hint shown only when downloads are impossible. */
@Composable
fun OfflineStrip(mode: AppMode, onClick: () -> Unit) {
    if (mode != AppMode.OFFLINE_VAULT) return
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Amber.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Amber.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, null, tint = Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Off campus", style = MaterialTheme.typography.titleSmall, color = Amber)
                Text(
                    "Saved papers still open. Connect to Amrita Wi-Fi or VPN to download.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Help", style = MaterialTheme.typography.labelLarge, color = Amber)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSheet(
    mode: AppMode,
    conn: ConnState,
    demoEnabled: Boolean,
    onDismiss: () -> Unit,
    onRecheck: () -> Unit,
    onDemo: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (title, body, color) = when (conn.status) {
                ConnStatus.CAMPUS -> Triple("Connected to campus", "Papers download straight from Amrita DSpace.", Emerald)
                ConnStatus.CHECKING -> Triple("Checking…", "Looking for dspace.amritanet.edu", MaterialTheme.colorScheme.onSurfaceVariant)
                ConnStatus.NO_CAMPUS -> Triple("Not on the campus network", "The paper server only answers on Amrita Wi-Fi or the Amrita VPN.", Amber)
                ConnStatus.NO_NETWORK -> Triple("No internet", "Your saved papers still work offline.", Amber)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRecheck) { Text("Check again") }

            if (conn.status != ConnStatus.CAMPUS) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Connect from your phone", style = MaterialTheme.typography.titleMedium)
                Step(1, "Install SoftEther VPN Client from the ICTS guide.")
                Step(2, "Sign in with your Amrita CMS username and password (sync the password on CMS first if it fails).")
                Step(3, "Connect, come back and tap Check again.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openUrl(context, Config.VPN_GUIDE_URL) }) { Text("Open VPN guide") }
                    TextButton(onClick = { openUrl(context, Config.CMS_URL) }) { Text("CMS password") }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Demo mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Try every feature off campus. Downloads become sample PDFs.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = demoEnabled, onCheckedChange = onDemo)
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

// ------------------------------------------------------------------ course picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursePickerSheet(
    branches: List<BranchEntity>,
    branch: String,
    semester: Int,
    onDismiss: () -> Unit,
    onPick: (String, Int) -> Unit,
) {
    var b by remember { mutableStateOf(branch) }
    var s by remember { mutableStateOf(semester) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your course", style = MaterialTheme.typography.headlineSmall)
            Text("Branch", style = MaterialTheme.typography.titleSmall)
            ChipRow(branches.map { it.code }, b, { it }) { b = it }
            branches.firstOrNull { it.code == b }?.let {
                Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Semester", style = MaterialTheme.typography.titleSmall)
            ChipRow((1..8).toList(), s, { "Sem $it" }) { s = it }
            Button(onClick = { onPick(b, s) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}

// ------------------------------------------------------------------ download options

/** Which papers a download covers. */
private data class Scope(val label: String, val start: Int, val end: Int)

/**
 * The single place where a download is configured: which papers (year range + exam type)
 * and where they go (app library, also the phone's Downloads folder, or one ZIP file).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    title: String,
    papers: List<PaperRow>,
    demoMode: Boolean,
    defaultMode: SaveMode,
    defaultZipName: String,
    /** When true the user already picked exact papers, so no range / type filters are shown. */
    picked: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (ids: List<String>, mode: SaveMode, zipName: String) -> Unit,
) {
    val years = papers.map { it.paper.year }.filter { it > 0 }
    val scopes = buildList {
        add(Scope("All years", Int.MIN_VALUE, Int.MAX_VALUE))
        add(Scope("Past 5 years", Packs.FIVE_YEAR.start, Packs.FIVE_YEAR.end))
        Packs.TWO_YEAR.forEach { r -> add(Scope(r.label, r.start, r.end)) }
    }.filter { sc -> sc.start == Int.MIN_VALUE || years.any { it in sc.start..sc.end } }

    var scope by remember { mutableStateOf(scopes.first()) }
    var filter by remember { mutableStateOf(ExamFilter.ALL) }
    var mode by remember { mutableStateOf(defaultMode) }
    var zipName by remember { mutableStateOf(defaultZipName) }

    val chosen = if (picked) papers else papers.filter {
        filter.matches(PaperKind.from(it.paper.kind)) &&
            (scope.start == Int.MIN_VALUE || it.paper.year in scope.start..scope.end)
    }
    val saved = chosen.count { it.isSaved && (it.dlDemo != true || demoMode) }
    val missing = chosen.size - saved

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Download", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall)

            if (!picked) {
                Text("Years", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                ChipRow(scopes, scope, { it.label }) { scope = it }
                Text("Exam type", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                ChipRow(ExamFilter.entries, filter, { it.label }) { filter = it }
            }

            Text("Save to", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            SaveMode.entries.forEach { m ->
                Surface(
                    onClick = { mode = m },
                    shape = MaterialTheme.shapes.medium,
                    color = if (mode == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, if (mode == m) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Column(Modifier.padding(end = 8.dp)) {
                            Text(m.label, style = MaterialTheme.typography.titleSmall)
                            Text(m.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (mode == SaveMode.ZIP) {
                OutlinedTextField(
                    zipName, { zipName = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("ZIP file name") }, suffix = { Text(".zip") },
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    chosen.isEmpty() -> "No papers match."
                    missing == 0 -> "All ${chosen.size} papers are already saved."
                    saved > 0 -> "${chosen.size} papers · $saved already saved · $missing to download"
                    else -> "${chosen.size} papers to download"
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val nothingToDo = chosen.isEmpty() || (missing == 0 && mode == SaveMode.APP_PRIVATE)
            Button(
                onClick = { onConfirm(chosen.map { it.paper.id }, mode, zipName.trim()) },
                enabled = !nothingToDo,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Icon(Icons.Filled.Download, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        chosen.isEmpty() -> "Nothing to download"
                        missing == 0 && mode == SaveMode.APP_PRIVATE -> "Already downloaded"
                        missing == 0 && mode == SaveMode.ZIP -> "Make ZIP of ${chosen.size} papers"
                        missing == 0 -> "Copy ${chosen.size} papers to Downloads"
                        else -> "Download $missing paper${if (missing == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ queue

/** Slim progress bar above the bottom navigation while downloads exist. */
@Composable
fun QueueBanner(queue: QueueSummary, onClick: () -> Unit) {
    if (!queue.visible) return
    val current = queue.rows.firstOrNull { it.state == "DOWNLOADING" }
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    queue.pending > 0 -> Icons.Filled.Download to MaterialTheme.colorScheme.primary
                    queue.failed > 0 -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
                    else -> Icons.Filled.CheckCircle to Emerald
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        queue.pending > 0 && queue.running ->
                            "Downloading ${queue.done + 1} of ${queue.total}" + (current?.let { " · ${it.subjectCode}" } ?: "")
                        queue.pending > 0 -> "Paused · ${queue.pending} left"
                        queue.failed > 0 -> "${queue.done} saved · ${queue.failed} failed"
                        else -> "All ${queue.total} papers ready"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(if (queue.pending > 0) "Details" else "Done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (queue.pending > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (queue.total == 0) 0f else (queue.done + queue.failed).toFloat() / queue.total },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    gapSize = 0.dp, drawStopIndicator = {},
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: QueueSummary,
    progress: Map<String, Float>,
    onDismiss: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Downloads", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${queue.done} saved · ${queue.pending} waiting · ${queue.failed} failed",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (queue.total == 0) 0f else (queue.done + queue.failed).toFloat() / queue.total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                gapSize = 0.dp, drawStopIndicator = {},
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (queue.pending > 0) {
                    if (queue.running) OutlinedButton(onClick = onPause) { Text("Pause") } else Button(onClick = onResume) { Text("Resume") }
                    TextButton(onClick = onCancel) { Text("Cancel all") }
                }
                if (queue.failed > 0) Button(onClick = onRetry) { Text("Retry failed") }
                if (queue.pending == 0) TextButton(onClick = { onClear(); onDismiss() }) { Text("Clear list") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(queue.rows, key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            when (r.state) {
                                "DONE", "SKIPPED" -> Icon(Icons.Filled.CheckCircle, null, tint = Emerald, modifier = Modifier.size(20.dp))
                                "FAILED" -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                "DOWNLOADING" -> {
                                    val f = progress[r.paperId] ?: -1f
                                    if (f < 0) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else CircularProgressIndicator(progress = { f }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                                else -> Icon(Icons.Filled.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${r.subjectCode} · ${r.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.state == "FAILED" && r.error != null) {
                                Text(r.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}
