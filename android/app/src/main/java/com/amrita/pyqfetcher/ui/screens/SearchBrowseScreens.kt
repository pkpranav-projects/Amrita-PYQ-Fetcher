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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.ui.components.EmptyState
import com.amrita.pyqfetcher.ui.components.PaperItem
import com.amrita.pyqfetcher.ui.components.RowCard
import com.amrita.pyqfetcher.ui.components.SectionTitle
import com.amrita.pyqfetcher.ui.theme.Amber

// ------------------------------------------------------------------ Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onReader: (String, Int) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val live by vm.liveState.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = {
                    TextField(
                        value = query, onValueChange = vm::setQuery,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = { Text("24CYS205, DBMS, 2025, mid-term…") },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "Clear") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.remember(query) }),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isBlank()) {
                if (history.isNotEmpty()) {
                    item { SectionTitle("Recent") { TextButton(onClick = vm::clearHistory) { Text("Clear") } } }
                    items(history, key = { it.query }) { h ->
                        RowCard(onClick = { vm.setQuery(h.query) }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(h.query)
                            }
                        }
                    }
                } else {
                    item {
                        EmptyState(
                            Icons.Filled.SearchOff, "Search papers",
                            "Try a course code (old codes work too), a subject like “OS”, a year, or “end-sem”.",
                        )
                    }
                }
            } else {
                item { SectionTitle("${results.size} result${if (results.size == 1) "" else "s"}") }
                items(results, key = { it.paper.id }) { r ->
                    PaperItem(
                        row = r, demoMode = mode == AppMode.DEMO, activity = activity[r.paper.id], showSubject = true,
                        onClick = { vm.remember(query); onReader(r.paper.id, -1) },
                        onDownload = { vm.downloadOne(r.paper.id) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::searchLive, Modifier.fillMaxWidth().height(48.dp), enabled = !live.loading) {
                        Icon(Icons.Filled.Public, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Search all of DSpace")
                    }
                }
                if (live.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                live.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                if (live.searched.isNotEmpty() && !live.loading && live.error == null) {
                    item { SectionTitle("${live.results.size} on DSpace") }
                }
                items(live.results, key = { "l" + it.path }) { item ->
                    RowCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium)
                            val files = live.files[item.path]
                            if (files == null) {
                                TextButton(onClick = { vm.loadFiles(item) }, contentPadding = PaddingValues(0.dp)) { Text("Show files") }
                            } else {
                                files.forEach { f ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text(f.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { vm.saveLiveFile(f) }) { Text("Save") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Browse DSpace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onBack: () -> Unit, vm: BrowseViewModel = hiltViewModel()) {
    val b by vm.ui.collectAsStateWithLifecycle()
    BackHandler(enabled = b.stack.size > 1) { vm.back() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = {
                    Column {
                        Text(b.content?.title?.ifBlank { null } ?: b.stack.lastOrNull()?.first ?: "DSpace", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (b.stack.size > 1) {
                            Text(
                                b.stack.dropLast(1).joinToString(" › ") { it.first },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { if (!vm.back()) onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = vm::reload) { Icon(Icons.Filled.Refresh, "Reload") } },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (b.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            b.error?.let { err ->
                item {
                    EmptyState(Icons.Filled.CloudOff, "Can't reach DSpace", err) {
                        Button(onClick = vm::reload) { Text("Try again") }
                    }
                }
            }
            val content = b.content
            if (content != null) {
                items(content.children, key = { "c" + it.path }) { c ->
                    RowCard(onClick = { vm.open(c.name, c.path) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, null, tint = Amber)
                            Spacer(Modifier.width(14.dp))
                            Text(c.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(content.files, key = { "f" + it.path }) { f ->
                    RowCard {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(14.dp))
                            Text(f.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.save(f) }) { Text("Save") }
                        }
                    }
                }
                if (content.children.isEmpty() && content.files.isEmpty() && !b.loading) {
                    item { EmptyState(Icons.Filled.Folder, "Empty folder", "Nothing is listed on this page.") }
                }
            }
            if (b.loading && content == null) item { CircularProgressIndicator(Modifier.padding(24.dp)) }
        }
    }
}
