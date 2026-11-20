package com.amrita.pyqfetcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import com.amrita.pyqfetcher.ui.LocalBottomInset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrita.pyqfetcher.data.db.VaultRow
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.ui.components.EmptyState
import com.amrita.pyqfetcher.ui.components.SectionTitle
import com.amrita.pyqfetcher.ui.components.Tag
import com.amrita.pyqfetcher.ui.components.formatBytes
import com.amrita.pyqfetcher.ui.theme.Amber
import com.amrita.pyqfetcher.ui.theme.Emerald

enum class LibrarySort(val label: String) { RECENT("Recently saved"), SUBJECT("Subject"), YEAR("Year"), SIZE("Size") }

@Composable
fun LibraryScreen(onReader: (String, Int) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(LibrarySort.SUBJECT) }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { vm.clearSelection() }

    val filtered = rows.filter {
        query.isBlank() || "${it.subjectCode} ${it.subjectName} ${it.year} ${it.title} ${PaperKind.from(it.kind).label}".contains(query, ignoreCase = true)
    }.let { list ->
        when (sort) {
            LibrarySort.RECENT -> list.sortedByDescending { it.downloadedAt }
            LibrarySort.SUBJECT -> list.sortedWith(compareBy({ it.subjectCode }, { -it.year }))
            LibrarySort.YEAR -> list.sortedByDescending { it.year }
            LibrarySort.SIZE -> list.sortedByDescending { it.sizeBytes }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Clear selection") }
                Text("${selection.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.shareZip { context.startActivity(it) } }) { Icon(Icons.Outlined.Share, "Share as ZIP") }
                IconButton(onClick = vm::exportToDownloads) { Icon(Icons.Outlined.FileDownload, "Save to phone Downloads") }
                IconButton(onClick = vm::deleteSelected) { Icon(Icons.Outlined.Delete, "Remove") }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Library", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${rows.size} papers · ${formatBytes(storage.realBytes + storage.demoBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        LibrarySort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                trailingIcon = { if (s == sort) Icon(Icons.Filled.Check, null) },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Select all") }, enabled = filtered.isNotEmpty(), onClick = { menu = false; vm.selectAll(filtered.map { it.paperId }) })
                        DropdownMenuItem(text = { Text("Check files") }, onClick = { menu = false; vm.verify() })
                        DropdownMenuItem(
                            text = { Text("Remove demo files") },
                            enabled = storage.demoBytes > 0, onClick = { menu = false; vm.cleanDemo() },
                        )
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomInset.current),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (rows.isNotEmpty()) {
                item {
                    com.amrita.pyqfetcher.ui.components.RowCard(modifier = Modifier.height(52.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) Text("Search your papers", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    inner()
                                },
                            )
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.FolderOff, "Nothing saved yet",
                        "Papers you download show up here and open without internet.",
                    )
                }
            } else if (filtered.isEmpty()) {
                item { EmptyState(Icons.Outlined.Search, "No matches", "Try a course code, subject or year.") }
            }
            if (sort == LibrarySort.SUBJECT) {
                filtered.groupBy { it.subjectCode }.forEach { (code, list) ->
                    item(key = "g$code") { SectionTitle("$code · ${list.first().subjectName}") }
                    items(list, key = { it.paperId }) { r -> Item(r, selection, selecting, vm, onReader) }
                }
            } else {
                items(filtered, key = { it.paperId }) { r -> Item(r, selection, selecting, vm, onReader) }
            }
        }
    }
}

@Composable
private fun Item(r: VaultRow, selection: Set<String>, selecting: Boolean, vm: LibraryViewModel, onReader: (String, Int) -> Unit) {
    val context = LocalContext.current
    LibraryItem(
        r, selected = r.paperId in selection, selecting = selecting,
        onClick = { if (selecting) vm.toggle(r.paperId) else onReader(r.paperId, -1) },
        onLongClick = { vm.toggle(r.paperId) },
        onShare = { context.startActivity(vm.shareOne(r)) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryItem(
    r: VaultRow,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val glass = com.amrita.pyqfetcher.ui.theme.LocalGlass.current
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) glass.fillSelected else glass.fill,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = com.amrita.pyqfetcher.ui.theme.glassBorder(glass),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Checkbox(selected, { onClick() })
            } else {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PictureAsPdf, null, tint = c.error.copy(alpha = 0.85f), modifier = Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${PaperKind.from(r.kind).label}${if (r.year > 0) " · ${r.year}" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (r.isDemo) Tag("Demo", Amber)
                    if (r.status == "SOLVED") Tag("Solved", Emerald)
                }
                Text(
                    "${formatBytes(r.sizeBytes)} · ${r.title}",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selecting) IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share", tint = c.onSurfaceVariant) }
        }
    }
}
