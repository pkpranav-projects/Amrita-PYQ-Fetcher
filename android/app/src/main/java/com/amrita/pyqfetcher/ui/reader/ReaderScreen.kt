package com.amrita.pyqfetcher.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amrita.pyqfetcher.domain.StudyStatus
import com.amrita.pyqfetcher.ui.components.StatusMenu
import com.amrita.pyqfetcher.ui.theme.Amber
import kotlinx.coroutines.delay

private val InvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: () -> Unit, onOpenPaper: (String) -> Unit = {}, vm: ReaderViewModel = hiltViewModel()) {
    val nextId by vm.nextPaperId.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val paper by vm.paper.collectAsStateWithLifecycle()
    val invert by vm.invert.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showBookmarks by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var addBookmark by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(jump) {
        val target = jump
        if (target != null) {
            val last = (ui.doc?.pageCount ?: 1) - 1
            if (target >= 0) listState.scrollToItem(target.coerceIn(0, last.coerceAtLeast(0)))
            jump = null
        }
    }

    val doc = ui.doc
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // Restore the last page once the document is ready, then keep saving the position.
    LaunchedEffect(doc) {
        if (doc != null) {
            listState.scrollToItem(ui.startPage)
            snapshotFlow { listState.firstVisibleItemIndex }.collect { vm.savePage(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(paper?.subjectCode ?: "Reader", fontWeight = FontWeight.Bold)
                        val p = paper?.paper
                        if (p != null) Text("${if (p.year > 0) p.year else ""} ${p.exam}".trim(), style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (vm.timerMinutes > 0) MockTimer(vm.timerMinutes)
                    IconButton(onClick = { vm.setInvert(!invert) }) {
                        Icon(Icons.Filled.InvertColors, "Midnight mode", tint = if (invert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { addBookmark = true }, enabled = doc != null) { Icon(Icons.Filled.BookmarkAdd, "Bookmark page") }
                    IconButton(onClick = { showBookmarks = true }) { Icon(Icons.Filled.Bookmarks, "Bookmarks") }
                    var overflow by remember { mutableStateOf(false) }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { overflow = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        androidx.compose.material3.DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Revision notes") }, leadingIcon = { Icon(Icons.Filled.EditNote, null) },
                                onClick = { overflow = false; showNote = true },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Share PDF") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, enabled = doc != null,
                                onClick = { overflow = false; vm.shareIntent()?.let { context.startActivity(it) } },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Open in another app") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, enabled = doc != null,
                                onClick = { overflow = false; vm.viewIntent()?.let { runCatching { context.startActivity(it) } } },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (doc != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Page ${currentPage + 1} / ${doc.pageCount}", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            StatusMenu(StudyStatus.from(paper?.paper?.status ?: "NOT_STARTED")) { vm.setStatus(it) }
                            nextId?.let { id -> TextButton(onClick = { onOpenPaper(id) }) { Text("Next paper ›") } }
                        }
                        if (doc.pageCount > 1) {
                            var drag by remember { mutableFloatStateOf(-1f) }
                            Slider(
                                value = if (drag >= 0) drag else currentPage.toFloat(),
                                onValueChange = { drag = it },
                                onValueChangeFinished = {
                                    // A plain tap can finish without any drag; ignore that.
                                    if (drag >= 0f) jump = drag.toInt()
                                    drag = -1f
                                },
                                valueRange = 0f..(doc.pageCount - 1).toFloat(),
                                steps = (doc.pageCount - 2).coerceAtLeast(0),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(if (invert) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceContainer)) {
            when {
                ui.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Opening…")
                }
                ui.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ui.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("Try again") }
                }
                doc != null -> {
                    PdfViewer(doc, invert, listState)
                }
            }
        }
    }

    if (addBookmark) {
        var label by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addBookmark = false },
            title = { Text("Bookmark page ${currentPage + 1}") },
            text = {
                OutlinedTextField(label, { label = it }, label = { Text("Label (e.g. “Q3 – RSA proof”)") }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { vm.addBookmark(currentPage, label); addBookmark = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { addBookmark = false }) { Text("Cancel") } },
        )
    }

    if (showBookmarks) {
        ModalBottomSheet(onDismissRequest = { showBookmarks = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Bookmarks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (bookmarks.isEmpty()) Text("No bookmarks yet.", Modifier.padding(vertical = 12.dp))
                bookmarks.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable { jump = b.page; showBookmarks = false }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.label, fontWeight = FontWeight.SemiBold)
                            Text("Page ${b.page + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteBookmark(b.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
        }
    }

    if (showNote) {
        var text by remember(note) { mutableStateOf(note) }
        ModalBottomSheet(onDismissRequest = { vm.saveNote(text); showNote = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Revision notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(180.dp), placeholder = { Text("Formulas, tricky questions, what to revise…") })
                Button(onClick = { vm.saveNote(text); showNote = false }) { Text("Save note") }
            }
        }
    }
}

@Composable
private fun MockTimer(minutes: Int) {
    val total = minutes * 60
    val remaining by produceState(total) {
        while (value > 0) {
            delay(1000)
            value -= 1
        }
    }
    val h = remaining / 3600
    val m = (remaining % 3600) / 60
    val s = remaining % 60
    Text(
        if (remaining == 0) "Time's up" else "%d:%02d:%02d".format(h, m, s),
        color = if (remaining < 600) MaterialTheme.colorScheme.error else Amber,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(end = 4.dp),
    )
}

/** Continuous-scroll PDF with pinch-to-zoom, pan and double-tap zoom. */
@Composable
private fun PdfViewer(doc: PdfDoc, invert: Boolean, listState: LazyListState) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val density = LocalDensity.current
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        val renderWidth = minOf((widthPx * 1.6f).toInt(), 1800)
        val vPad = with(density) { (heightPx * (1f - 1f / scale) / 2f).toDp() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale <= 1.01f) {
                                    scale = 1f
                                    offsetX = 0f
                                } else {
                                    val maxX = widthPx * (scale - 1f) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    listState.dispatchRawDelta(-pan.y / scale)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1.01f) { scale = 1f; offsetX = 0f } else scale = 2.5f
                    })
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                },
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = scale <= 1.01f,
                contentPadding = PaddingValues(vertical = vPad + 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(doc.pageCount, key = { it }) { index ->
                    PdfPageView(doc, index, renderWidth, invert)
                }
            }
        }
    }
}

@Composable
private fun PdfPageView(doc: PdfDoc, index: Int, renderWidth: Int, invert: Boolean) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, doc, index, renderWidth) {
        value = runCatching { doc.render(index, renderWidth).asImageBitmap() }.getOrNull()
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(doc.aspects[index]).background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, "Page ${index + 1}", Modifier.fillMaxSize(), contentScale = ContentScale.FillWidth, colorFilter = if (invert) InvertFilter else null)
        } else {
            CircularProgressIndicator(Modifier.width(28.dp), strokeWidth = 2.dp)
        }
    }
}
