package com.amrita.pyqfetcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.ui.AppViewModel
import com.amrita.pyqfetcher.ui.LocalBottomInset
import com.amrita.pyqfetcher.ui.components.ChipRow
import com.amrita.pyqfetcher.ui.components.ConnectionPill
import com.amrita.pyqfetcher.ui.components.CoursePickerSheet
import com.amrita.pyqfetcher.ui.components.DownloadSheet
import com.amrita.pyqfetcher.ui.components.EmptyState
import com.amrita.pyqfetcher.ui.components.OfflineStrip
import com.amrita.pyqfetcher.ui.components.PaperItem
import com.amrita.pyqfetcher.ui.components.RowCard
import com.amrita.pyqfetcher.ui.components.SectionTitle
import com.amrita.pyqfetcher.ui.components.SubjectItem
import com.amrita.pyqfetcher.ui.theme.Emerald
import com.amrita.pyqfetcher.ui.theme.GlassPanel

// ------------------------------------------------------------------ Onboarding

@Composable
fun OnboardingScreen(app: AppViewModel, onDone: () -> Unit) {
    val branches by app.branches.collectAsStateWithLifecycle()
    var branch by remember { mutableStateOf("CYS") }
    var sem by remember { mutableIntStateOf(1) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick your branch and semester. You can change these any time.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Branch", style = MaterialTheme.typography.titleSmall)
                ChipRow(branches.map { it.code }, branch, { it }) { branch = it }
                branches.firstOrNull { it.code == branch }?.let {
                    Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text("Semester", style = MaterialTheme.typography.titleSmall)
                ChipRow((1..8).toList(), sem, { "$it" }) { sem = it }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Papers come from Amrita's DSpace server, which works on campus Wi-Fi or the Amrita VPN. " +
                "Downloaded papers stay on your phone and open without internet. No account needed.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { app.completeOnboarding(branch, sem); onDone() }, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Continue")
        }
    }
}

// ------------------------------------------------------------------ Home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: AppViewModel,
    onSearch: () -> Unit,
    onSubject: (String) -> Unit,
    onReader: (String, Int) -> Unit,
    onConnection: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val prefs by app.prefs.collectAsStateWithLifecycle()
    val mode by app.mode.collectAsStateWithLifecycle()
    val conn by app.conn.collectAsStateWithLifecycle()
    val branches by app.branches.collectAsStateWithLifecycle()
    val loaded by vm.subjects.collectAsStateWithLifecycle()
    val subjects = loaded.orEmpty()
    val semPapers by vm.semesterPapers.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val newPapers by vm.newPapers.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val p = prefs ?: return
    var pickCourse by remember { mutableStateOf(false) }
    var downloadSheet by remember { mutableStateOf(false) }

    val total = subjects.sumOf { it.paperCount }
    val saved = subjects.sumOf { it.savedCount }
    val branchName = branches.firstOrNull { it.code == p.branch }?.name ?: p.branch

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomInset.current),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Amrita PYQ", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    ConnectionPill(mode, conn, onConnection)
                }
            }

            // Search, the way most apps put it: a pill at the top.
            item {
                RowCard(onClick = onSearch, modifier = Modifier.height(52.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("Search papers", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                GlassPanel(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Semester ${p.semester}", style = MaterialTheme.typography.headlineSmall)
                                Text(branchName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { pickCourse = true }) { Text("Change") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { if (total == 0) 0f else saved.toFloat() / total },
                                    modifier = Modifier.size(56.dp), color = Emerald, strokeWidth = 5.dp,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), strokeCap = StrokeCap.Round,
                                )
                                Text(if (total == 0) "–" else "${saved * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("$saved of $total papers offline", style = MaterialTheme.typography.titleSmall)
                                Text("${subjects.size} subjects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (total > 0 && saved == total) {
                            FilledTonalButton(onClick = { downloadSheet = true }, Modifier.fillMaxWidth().height(48.dp)) { Text("Save options") }
                        } else {
                            Button(onClick = { downloadSheet = true }, enabled = total > 0, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Download papers")
                            }
                        }
                    }
                }
            }

            item { OfflineStrip(mode, onConnection) }

            if (newPapers.isNotEmpty()) {
                item { SectionTitle("Recently added") }
                items(newPapers.take(5), key = { "n" + it.paper.id }) { r ->
                    PaperItem(
                        row = r, demoMode = mode == AppMode.DEMO, activity = activity[r.paper.id], showSubject = true,
                        onClick = { onReader(r.paper.id, -1) }, onDownload = { vm.downloadOne(r.paper.id) },
                    )
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionTitle("Continue reading") }
                items(recent, key = { "r" + it.paper.id }) { r ->
                    RowCard(onClick = { onReader(r.paper.id, r.paper.lastPage) }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${r.subjectCode} · ${PaperKind.from(r.paper.kind).label} ${if (r.paper.year > 0) r.paper.year else ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text("Page ${r.paper.lastPage + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Subjects") }
            if (loaded != null && subjects.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.FolderOff, "No papers yet",
                        "On campus Wi-Fi or VPN, pull down to check DSpace for ${p.branch} papers.",
                    )
                }
            }
            items(subjects, key = { it.id }) { s -> SubjectItem(s) { onSubject(s.id) } }
        }
    }

    if (pickCourse) {
        CoursePickerSheet(branches, p.branch, p.semester, onDismiss = { pickCourse = false }) { b, s ->
            app.setBranch(b); app.setSemester(s); pickCourse = false
        }
    }
    if (downloadSheet) {
        DownloadSheet(
            title = "Semester ${p.semester}",
            papers = semPapers,
            demoMode = mode == AppMode.DEMO,
            defaultMode = p.saveMode,
            defaultZipName = "${p.branch}_Sem${p.semester}_Papers",
            onDismiss = { downloadSheet = false },
        ) { ids, m, zip ->
            vm.download(ids, "Semester ${p.semester}", m, zip)
            downloadSheet = false
        }
    }
}
