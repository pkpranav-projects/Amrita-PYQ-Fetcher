package com.amrita.pyqfetcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.domain.ExamFilter
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.ui.components.ChipRow
import com.amrita.pyqfetcher.ui.components.DownloadSheet
import com.amrita.pyqfetcher.ui.components.EmptyState
import com.amrita.pyqfetcher.ui.components.PaperItem
import com.amrita.pyqfetcher.ui.components.SectionTitle
import com.amrita.pyqfetcher.ui.components.Tag
import com.amrita.pyqfetcher.ui.theme.Amber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectScreen(
    defaultMode: SaveMode,
    onBack: () -> Unit,
    onReader: (paperId: String, page: Int, timer: Int) -> Unit,
    vm: SubjectViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val papers by vm.papers.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val selecting = selection.isNotEmpty()
    var sheetFor by remember { mutableStateOf<List<String>?>(null) } // null = closed; empty = whole subject
    var practice by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val shown = papers.filter { filter.matches(PaperKind.from(it.paper.kind)) }
    val byYear = shown.groupBy { it.paper.year }.toSortedMap(compareByDescending { it })
    BackHandler(enabled = selecting) { vm.clearSelection() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = { Text(if (selecting) "${selection.size} selected" else summary?.code ?: "") },
                navigationIcon = {
                    if (selecting) IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") }
                    else IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!selecting && papers.isNotEmpty()) {
                        IconButton(onClick = { vm.pickPractice { id, min -> practice = id to min } }) {
                            Icon(Icons.Filled.Timer, "Practice with a random paper")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = com.amrita.pyqfetcher.ui.theme.LocalGlass.current.barTint) {
                Row(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(
                        onClick = { sheetFor = if (selecting) selection.toList() else emptyList() },
                        enabled = papers.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Download, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (selecting) "Download ${selection.size} selected" else "Download",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val s = summary
                if (s != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.name, style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Semester ${s.semester} · ${s.paperCount} papers · ${s.savedCount} saved",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.legacyList.isNotEmpty()) Tag("Includes old code ${s.legacyList.joinToString(" / ")}", Amber)
                        Spacer(Modifier.height(4.dp))
                        ChipRow(ExamFilter.entries, filter, { it.label }) { vm.setFilter(it) }
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Filled.FolderOff, "No papers", "Nothing matches this exam type.") }
            byYear.forEach { (year, list) ->
                item(key = "y$year") { SectionTitle(if (year > 0) year.toString() else "Year unknown") }
                items(list, key = { it.paper.id }) { r ->
                    PaperItem(
                        row = r,
                        demoMode = mode == AppMode.DEMO,
                        activity = activity[r.paper.id],
                        selected = r.paper.id in selection,
                        onClick = { if (selecting) vm.toggle(r.paper.id) else onReader(r.paper.id, -1, 0) },
                        onLongClick = { vm.toggle(r.paper.id) },
                        onDownload = { vm.downloadOne(r.paper.id) },
                    )
                }
            }
        }
    }

    sheetFor?.let { picked ->
        val list = if (picked.isEmpty()) papers else papers.filter { it.paper.id in picked }
        val s = summary
        DownloadSheet(
            title = if (picked.isEmpty()) "${s?.code ?: ""} · ${s?.name ?: ""}" else "${picked.size} selected papers",
            papers = list,
            demoMode = mode == AppMode.DEMO,
            defaultMode = defaultMode,
            defaultZipName = "${s?.code ?: "Papers"}_Past_Papers",
            picked = picked.isNotEmpty(),
            onDismiss = { sheetFor = null },
        ) { ids, m, zip ->
            vm.download(ids, m, zip)
            sheetFor = null
        }
    }

    practice?.let { (id, minutes) ->
        AlertDialog(
            onDismissRequest = { practice = null },
            title = { Text("Practice paper") },
            text = { Text("Open a random unsolved paper. Add a ${minutes / 60}-hour exam timer?") },
            confirmButton = { TextButton(onClick = { practice = null; onReader(id, -1, minutes) }) { Text("With timer") } },
            dismissButton = { TextButton(onClick = { practice = null; onReader(id, -1, 0) }) { Text("Just open") } },
        )
    }
}
