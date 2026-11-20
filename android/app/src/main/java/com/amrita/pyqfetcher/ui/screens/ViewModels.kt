package com.amrita.pyqfetcher.ui.screens

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.core.Config
import com.amrita.pyqfetcher.core.ModeProvider
import com.amrita.pyqfetcher.core.PrefsState
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.PaperRow
import com.amrita.pyqfetcher.data.db.SearchHistoryEntity
import com.amrita.pyqfetcher.data.db.SubjectSummary
import com.amrita.pyqfetcher.data.db.VaultRow
import com.amrita.pyqfetcher.data.net.PageContent
import com.amrita.pyqfetcher.data.net.Resource
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.data.repo.DownloadOutcome
import com.amrita.pyqfetcher.data.repo.DownloadRepository
import com.amrita.pyqfetcher.data.repo.FileStore
import com.amrita.pyqfetcher.data.repo.LiveRepository
import com.amrita.pyqfetcher.domain.ExamFilter
import com.amrita.pyqfetcher.domain.PaperKind
import com.amrita.pyqfetcher.domain.SearchEngine
import com.amrita.pyqfetcher.domain.StudyStatus
import com.amrita.pyqfetcher.ui.PaperActions
import com.amrita.pyqfetcher.ui.PaperActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private fun <T> Flow<T>.state(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(5000), initial)

// ------------------------------------------------------------------ Home

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    prefsState: PrefsState,
    db: AppDatabase,
    private val actions: PaperActions,
    modeProvider: ModeProvider,
    private val sync: com.amrita.pyqfetcher.core.SyncManager,
    private val monitor: com.amrita.pyqfetcher.core.ConnectivityMonitor,
) : ViewModel() {
    /** Pull-to-refresh: re-check the server, then look for new papers if we're on campus. */
    val refreshing = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch {
        refreshing.value = true
        monitor.refresh(immediate = true)
        kotlinx.coroutines.delay(300)
        val st = monitor.state.first { it.status != com.amrita.pyqfetcher.core.ConnStatus.CHECKING }
        if (st.status == com.amrita.pyqfetcher.core.ConnStatus.CAMPUS) {
            sync.start()
            sync.state.first { !it.running }
            sync.state.value.message?.let { actions.say(it) }
        } else {
            actions.say("Not connected to the campus network")
        }
        refreshing.value = false
    }

    private val course = prefsState.state.filterNotNull().map { it.branch to it.semester }
    private val demo = modeProvider.mode.map { it == AppMode.DEMO }

    /** null until the first database read, so the screen doesn't flash "no papers". */
    val subjects: StateFlow<List<SubjectSummary>?> = combine(course, demo) { c, d -> Triple(c.first, c.second, d) }
        .flatMapLatest { (b, s, d) -> db.catalog().observeSubjects(b, s, d) }.state(viewModelScope, null)
    val semesterPapers: StateFlow<List<PaperRow>> = course
        .flatMapLatest { (b, s) -> db.catalog().observeSemesterPapers(b, s) }.state(viewModelScope, emptyList())
    val recent: StateFlow<List<PaperRow>> = db.catalog().observeRecent(3).state(viewModelScope, emptyList())

    /** Papers a live sync found in the last 14 days for the student's semester. */
    val newPapers: StateFlow<List<PaperRow>> = course
        .flatMapLatest { (b, s) -> db.catalog().observeNew(b, s, System.currentTimeMillis() - 14L * 24 * 3600 * 1000) }
        .state(viewModelScope, emptyList())

    fun downloadOne(id: String) = viewModelScope.launch { actions.queueOne(id) }
    val activity: StateFlow<Map<String, PaperActivity>> = actions.activity.state(viewModelScope, emptyMap())

    fun download(ids: List<String>, label: String, mode: SaveMode, zip: String) =
        viewModelScope.launch { actions.queue(ids, label, mode, zip) }
}

// ------------------------------------------------------------------ Subject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectViewModel @Inject constructor(
    savedState: SavedStateHandle,
    db: AppDatabase,
    private val actions: PaperActions,
    modeProvider: ModeProvider,
) : ViewModel() {
    val subjectId: String = checkNotNull(savedState["subjectId"])
    val mode: StateFlow<AppMode> = modeProvider.mode
    val summary: StateFlow<SubjectSummary?> = modeProvider.mode.flatMapLatest { db.catalog().observeAllSubjects(it == AppMode.DEMO) }
        .map { list -> list.firstOrNull { it.id == subjectId } }.state(viewModelScope, null)
    val papers: StateFlow<List<PaperRow>> = db.catalog().observePapers(subjectId).state(viewModelScope, emptyList())
    val activity: StateFlow<Map<String, PaperActivity>> = actions.activity.state(viewModelScope, emptyMap())
    val filter = MutableStateFlow(ExamFilter.ALL)
    val selection = MutableStateFlow<Set<String>>(emptySet())

    fun setFilter(f: ExamFilter) { filter.value = f }
    fun toggle(id: String) = selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { selection.value = emptySet() }

    fun download(ids: List<String>, mode: SaveMode, zip: String) = viewModelScope.launch {
        actions.queue(ids, summary.value?.code ?: "subject", mode, zip)
        selection.value = emptySet()
    }

    fun downloadOne(id: String) = viewModelScope.launch { actions.queueOne(id) }

    fun setStatus(id: String, s: StudyStatus) = viewModelScope.launch { actions.setStatus(id, s) }

    /** Practice: a random unsolved paper (End-Sem preferred), with the exam duration for the timer. */
    fun pickPractice(onPicked: (paperId: String, minutes: Int) -> Unit) {
        val list = papers.value
        if (list.isEmpty()) return actions.say("No papers for this subject yet")
        val pool = list.filter { StudyStatus.from(it.paper.status) != StudyStatus.SOLVED }.ifEmpty { list }
        val pick = pool.random()
        onPicked(pick.paper.id, if (PaperKind.from(pick.paper.kind) == PaperKind.END_SEM) 180 else 120)
    }
}

// ------------------------------------------------------------------ Search

data class LiveSearchState(
    val loading: Boolean = false,
    val results: List<Resource> = emptyList(),
    val files: Map<String, List<Resource>> = emptyMap(),
    val error: String? = null,
    val searched: String = "",
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val db: AppDatabase,
    private val live: LiveRepository,
    private val actions: PaperActions,
    modeProvider: ModeProvider,
) : ViewModel() {
    val query = MutableStateFlow("")
    val mode: StateFlow<AppMode> = modeProvider.mode
    val history = db.study().observeSearchHistory().state(viewModelScope, emptyList())
    val results: StateFlow<List<PaperRow>> = combine(db.catalog().observeAllPapers(), query) { rows, q ->
        SearchEngine.search(rows, q)
    }.state(viewModelScope, emptyList())
    val activity: StateFlow<Map<String, PaperActivity>> = actions.activity.state(viewModelScope, emptyMap())

    private val _live = MutableStateFlow(LiveSearchState())
    val liveState: StateFlow<LiveSearchState> = _live

    fun setQuery(q: String) {
        query.value = q
        if (q.isBlank()) _live.value = LiveSearchState()
    }

    fun remember(q: String) = viewModelScope.launch {
        if (q.isNotBlank()) db.study().addSearch(SearchHistoryEntity(q.trim(), System.currentTimeMillis()))
    }

    fun clearHistory() = viewModelScope.launch { db.study().clearSearchHistory() }

    fun searchLive() {
        val q = query.value.trim()
        if (q.isEmpty()) return
        remember(q)
        if (actions.mode == AppMode.OFFLINE_VAULT) {
            _live.value = LiveSearchState(error = "Searching DSpace needs the campus network or VPN.", searched = q)
            return
        }
        viewModelScope.launch {
            _live.value = LiveSearchState(loading = true, searched = q)
            _live.value = try {
                LiveSearchState(results = live.search(q, actions.demo), searched = q)
            } catch (e: Exception) {
                LiveSearchState(error = e.message ?: "Search failed", searched = q)
            }
        }
    }

    fun loadFiles(item: Resource) = viewModelScope.launch {
        try {
            val all = live.filesOf(item.path, actions.demo)
            // A session holds every subject's PDF; show the ones matching the search when there are any.
            val words = _live.value.searched.lowercase().split(' ').filter { it.length >= 3 }
            val matching = all.filter { f -> words.any { f.name.lowercase().contains(it) } }
            val files = matching.ifEmpty { all }
            _live.update { it.copy(files = it.files + (item.path to files)) }
            if (files.isEmpty()) actions.say("No PDF files on that page")
        } catch (e: Exception) {
            actions.say(e.message ?: "Could not open item")
        }
    }

    fun saveLiveFile(file: Resource) = viewModelScope.launch { saveLive(file, db, live, actions) }

    fun downloadOne(id: String) = viewModelScope.launch { actions.queueOne(id) }
}

/** Indexes a file found live on DSpace into the catalog and queues it. Shared by search and the browser. */
private suspend fun saveLive(file: Resource, db: AppDatabase, live: LiveRepository, actions: PaperActions) {
    if (actions.demo) return actions.say("That's a demo sample. Real papers download on the campus network.")
    val stats = live.indexFiles(listOf(file))
    if (stats.pdfs == 0) return actions.say("That link isn't a PDF")
    if (stats.unclassified > 0) return actions.say("Couldn't tell which course “${file.name}” belongs to")
    val path = file.path.substringBefore('?')
    val ids = db.catalog().allSubjects().flatMap { db.catalog().papersOf(it.id) }.filter { it.paper.path == path }.map { it.paper.id }
    ids.forEach { actions.queueOne(it) }
    actions.say("Added to your library")
}

// ------------------------------------------------------------------ Library

data class StorageInfo(val realBytes: Long = 0, val demoBytes: Long = 0)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    db: AppDatabase,
    private val downloads: DownloadRepository,
    private val files: FileStore,
    private val actions: PaperActions,
    private val messages: com.amrita.pyqfetcher.ui.Messages,
    private val appScope: com.amrita.pyqfetcher.core.AppScope,
) : ViewModel() {
    /** Papers removed but still undoable; hidden from the list until the snackbar goes away. */
    private val pendingDelete = MutableStateFlow<Set<String>>(emptySet())

    val rows: StateFlow<List<VaultRow>> = combine(db.downloads().observeVault(), pendingDelete) { rows, hidden ->
        rows.filter { it.paperId !in hidden }
    }.state(viewModelScope, emptyList())
    val storage: StateFlow<StorageInfo> = combine(db.downloads().observeRealBytes(), db.downloads().observeDemoBytes()) { r, d ->
        StorageInfo(r, d)
    }.state(viewModelScope, StorageInfo())
    val selection = MutableStateFlow<Set<String>>(emptySet())
    val busy = MutableStateFlow(false)

    fun toggle(id: String) = selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll(ids: List<String>) { selection.value = ids.toSet() }

    private fun selectedRows(): List<VaultRow> = rows.value.filter { it.paperId in selection.value }

    fun shareZip(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val sel = selectedRows()
        if (sel.isEmpty()) return@launch
        busy.value = true
        val name = sel.map { it.subjectCode }.distinct().let { if (it.size == 1) "${it[0]}_Past_Papers" else "AmritaPYQ_Papers" }
        val intent = withContext(Dispatchers.IO) { files.zipShareIntent(name, sel) }
        busy.value = false
        onReady(intent)
    }

    fun shareOne(row: VaultRow): Intent = files.shareIntent(File(row.localPath), row.title)

    fun exportToDownloads() = viewModelScope.launch {
        val sel = selectedRows()
        busy.value = true
        val n = withContext(Dispatchers.IO) { files.exportToDownloads(sel.map { File(it.localPath) }) }
        busy.value = false
        selection.value = emptySet()
        actions.say("Saved $n file${if (n == 1) "" else "s"} to Downloads/AmritaPYQ")
    }

    /** Removes with Undo, like Gmail or Files: the files are only deleted once the snackbar is gone. */
    fun deleteSelected() {
        val ids = selection.value.toSet()
        if (ids.isEmpty()) return
        selection.value = emptySet()
        pendingDelete.update { it + ids }
        messages.post(
            com.amrita.pyqfetcher.ui.Msg("Removed ${ids.size} paper${if (ids.size == 1) "" else "s"}", action = "Undo") { undo ->
                if (undo) {
                    pendingDelete.update { it - ids }
                } else {
                    // App scope: finish the delete even if this screen is already gone.
                    appScope.scope.launch { downloads.delete(ids.toList()); pendingDelete.update { it - ids } }
                }
            },
        )
    }

    fun cleanDemo() = viewModelScope.launch {
        val freed = downloads.cleanDemoAndCache()
        actions.say("Freed ${com.amrita.pyqfetcher.ui.components.formatBytes(freed)} of demo and temporary files")
    }

    fun verify() = viewModelScope.launch {
        busy.value = true
        val r = downloads.verifyAll()
        busy.value = false
        actions.say(if (r.removed == 0) "All ${r.checked} files are OK" else "Removed ${r.removed} broken file(s) out of ${r.checked}")
    }
}

// ------------------------------------------------------------------ More (sync)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val sync: com.amrita.pyqfetcher.core.SyncManager,
    private val prefsState: PrefsState,
) : ViewModel() {
    val state: StateFlow<com.amrita.pyqfetcher.core.SyncUi> = sync.state

    fun startSync() = sync.start()
    fun stopSync() = sync.stop()
    fun setAutoSync(v: Boolean) = viewModelScope.launch { prefsState.prefs.setAutoSync(v) }
}

// ------------------------------------------------------------------ Browse DSpace

data class BrowserUi(
    val stack: List<Pair<String, String>> = emptyList(),
    val content: PageContent? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val db: AppDatabase,
    private val live: LiveRepository,
    private val actions: PaperActions,
) : ViewModel() {
    private val _ui = MutableStateFlow(BrowserUi())
    val ui: StateFlow<BrowserUi> = _ui

    init { open("Exam papers", Config.EXAM_ROOT) }

    fun open(title: String, path: String) {
        if (actions.mode == AppMode.OFFLINE_VAULT) {
            _ui.value = BrowserUi(error = "Browsing DSpace needs the campus network, VPN or Demo mode.")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val content = live.browse(path, actions.demo)
                _ui.update { it.copy(stack = it.stack + (title to path), content = content, loading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Could not load page") }
            }
        }
    }

    fun back(): Boolean {
        val st = _ui.value.stack
        if (st.size <= 1) return false
        val target = st[st.size - 2]
        _ui.update { it.copy(stack = st.dropLast(2)) }
        open(target.first, target.second)
        return true
    }

    fun reload() {
        val st = _ui.value.stack
        if (st.isEmpty()) return open("Exam papers", Config.EXAM_ROOT)
        val last = st.last()
        _ui.update { it.copy(stack = st.dropLast(1)) }
        open(last.first, last.second)
    }

    fun save(file: Resource) = viewModelScope.launch { saveLive(file, db, live, actions) }
}
