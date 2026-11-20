package com.amrita.pyqfetcher.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amrita.pyqfetcher.core.PrefsState
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.BookmarkEntity
import com.amrita.pyqfetcher.data.db.PaperNoteEntity
import com.amrita.pyqfetcher.data.db.PaperRow
import com.amrita.pyqfetcher.data.repo.DownloadOutcome
import com.amrita.pyqfetcher.data.repo.DownloadRepository
import com.amrita.pyqfetcher.data.repo.FileStore
import com.amrita.pyqfetcher.domain.StudyStatus
import com.amrita.pyqfetcher.ui.PaperActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReaderUi(
    val loading: Boolean = true,
    val error: String? = null,
    val doc: PdfDoc? = null,
    val file: File? = null,
    val startPage: Int = 0,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val db: AppDatabase,
    private val downloads: DownloadRepository,
    private val actions: PaperActions,
    private val files: FileStore,
    private val prefsState: PrefsState,
) : ViewModel() {
    val paperId: String = checkNotNull(savedState["paperId"])
    private val pageArg: Int = savedState.get<Int>("page") ?: -1
    val timerMinutes: Int = savedState.get<Int>("timer") ?: 0

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui

    val paper: StateFlow<PaperRow?> = db.catalog().observePaperRow(paperId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val bookmarks: StateFlow<List<BookmarkEntity>> = db.study().observeBookmarksFor(paperId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val invert: StateFlow<Boolean> = prefsState.state.map { it?.pdfInvert ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val note = MutableStateFlow("")

    /** The paper after this one in its subject (newest first), for "Next paper" while revising. */
    val nextPaperId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { note.value = db.study().note(paperId)?.text.orEmpty() }
        viewModelScope.launch {
            val row = db.catalog().paperRow(paperId) ?: return@launch
            val list = db.catalog().papersOf(row.paper.subjectId)
                .sortedWith(compareByDescending<PaperRow> { it.paper.year }.thenBy { it.paper.kind }.thenBy { it.paper.title })
            val i = list.indexOfFirst { it.paper.id == paperId }
            nextPaperId.value = list.getOrNull(i + 1)?.paper?.id
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.value = ReaderUi(loading = true)
            val row = db.catalog().paperRow(paperId)
            if (row == null) {
                _ui.value = ReaderUi(loading = false, error = "Paper not found")
                return@launch
            }
            val outcome = actions.downloadNow(paperId)
            if (outcome is DownloadOutcome.Failed) {
                _ui.value = ReaderUi(loading = false, error = outcome.reason)
                return@launch
            }
            val file = downloads.localFile(paperId, actions.demo)
            if (file == null) {
                _ui.value = ReaderUi(loading = false, error = "The saved file is missing or is not a valid PDF. Try downloading again.")
                return@launch
            }
            try {
                val doc = PdfDoc.open(file)
                val start = (if (pageArg >= 0) pageArg else row.paper.lastPage).coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
                _ui.value = ReaderUi(loading = false, doc = doc, file = file, startPage = start)
                if (StudyStatus.from(row.paper.status) == StudyStatus.NOT_STARTED) {
                    db.catalog().setStatus(paperId, StudyStatus.IN_PROGRESS.name)
                }
            } catch (e: Exception) {
                _ui.value = ReaderUi(loading = false, error = "Could not open this PDF: ${e.message}")
            }
        }
    }

    fun savePage(page: Int) = viewModelScope.launch {
        db.catalog().setProgress(paperId, page, System.currentTimeMillis())
    }

    fun addBookmark(page: Int, label: String) = viewModelScope.launch {
        db.study().addBookmark(BookmarkEntity(paperId = paperId, page = page, label = label.ifBlank { "Page ${page + 1}" }, createdAt = System.currentTimeMillis()))
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { db.study().deleteBookmarkById(id) }

    fun saveNote(text: String) = viewModelScope.launch {
        note.value = text
        if (text.isBlank()) db.study().deleteNote(paperId)
        else db.study().upsertNote(PaperNoteEntity(paperId, text, System.currentTimeMillis()))
    }

    fun setStatus(s: StudyStatus) = viewModelScope.launch { actions.setStatus(paperId, s) }
    fun setInvert(v: Boolean) = viewModelScope.launch { prefsState.prefs.setPdfInvert(v) }

    fun shareIntent() = _ui.value.file?.let { files.shareIntent(it, paper.value?.paper?.title ?: "Question paper") }
    fun viewIntent() = _ui.value.file?.let { files.viewIntent(it) }

    override fun onCleared() {
        _ui.value.doc?.close()
        super.onCleared()
    }
}
