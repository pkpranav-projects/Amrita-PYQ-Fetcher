package com.amrita.pyqfetcher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amrita.pyqfetcher.core.AppMode
import com.amrita.pyqfetcher.core.ConnState
import com.amrita.pyqfetcher.core.ConnectivityMonitor
import com.amrita.pyqfetcher.core.HostMode
import com.amrita.pyqfetcher.core.ModeProvider
import com.amrita.pyqfetcher.core.PrefsState
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.BranchEntity
import com.amrita.pyqfetcher.data.db.QueueRow
import com.amrita.pyqfetcher.data.prefs.SaveMode
import com.amrita.pyqfetcher.data.repo.DownloadProgress
import com.amrita.pyqfetcher.data.prefs.Prefs
import com.amrita.pyqfetcher.data.prefs.ThemeMode
import com.amrita.pyqfetcher.data.repo.DownloadRepository
import com.amrita.pyqfetcher.work.DownloadScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueueSummary(
    val rows: List<QueueRow>,
    val running: Boolean,
) {
    val total get() = rows.size
    val done get() = rows.count { it.state == "DONE" || it.state == "SKIPPED" }
    val failed get() = rows.count { it.state == "FAILED" }
    val pending get() = rows.count { it.state == "PENDING" || it.state == "DOWNLOADING" }
    val visible get() = rows.isNotEmpty()
}

/** App-wide state: preferences, connectivity, mode and the shared bulk-download queue. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefsState: PrefsState,
    private val monitor: ConnectivityMonitor,
    modeProvider: ModeProvider,
    private val scheduler: DownloadScheduler,
    private val downloads: DownloadRepository,
    private val db: AppDatabase,
    progress: DownloadProgress,
    private val messages: Messages,
) : ViewModel() {

    /** Byte-level progress of in-flight downloads (paperId → 0..1). */
    val progress: StateFlow<Map<String, Float>> = progress.map

    val prefs: StateFlow<Prefs?> = prefsState.state
    val conn: StateFlow<ConnState> = monitor.state
    val mode: StateFlow<AppMode> = modeProvider.mode

    val branches: StateFlow<List<BranchEntity>> = db.catalog().observeBranches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queue: StateFlow<QueueSummary> = combine(db.queue().observeQueue(), scheduler.isRunning()) { rows, running ->
        QueueSummary(rows, running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueSummary(emptyList(), false))

    private val p get() = prefsState.prefs

    init {
        // Downloads interrupted by a restart pick up again once we know the connection state.
        viewModelScope.launch {
            conn.first { it.status != com.amrita.pyqfetcher.core.ConnStatus.CHECKING }
            prefs.filterNotNull().first()
            // Finished rows from an earlier session are just noise on a fresh start.
            if (db.queue().activeJobIds().isEmpty()) scheduler.clearFinished()
            if (mode.value != AppMode.OFFLINE_VAULT && db.queue().activeJobIds().isNotEmpty()) {
                scheduler.resume(mode.value == AppMode.DEMO)
            }
        }
    }

    fun refreshConnection() = monitor.refresh(immediate = true)
    fun completeOnboarding(branch: String, sem: Int) = viewModelScope.launch { p.completeOnboarding(branch, sem) }
    fun setBranch(v: String) = viewModelScope.launch { p.setBranch(v) }
    fun setSemester(v: Int) = viewModelScope.launch { p.setSemester(v) }
    fun setHostMode(v: HostMode) = viewModelScope.launch { p.setHostMode(v); monitor.refresh(immediate = true) }
    fun setDemo(v: Boolean) = viewModelScope.launch { p.setDemoMode(v) }
    fun setInvert(v: Boolean) = viewModelScope.launch { p.setPdfInvert(v) }
    fun setSaveMode(v: SaveMode) = viewModelScope.launch { p.setSaveMode(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { p.setThemeMode(v) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { p.setDynamicColor(v) }
    fun markNotifAsked() = viewModelScope.launch { p.setNotifAsked(true) }
    fun setAppLock(v: Boolean) = viewModelScope.launch { p.setAppLock(v) }
    fun setLockAfter(sec: Int) = viewModelScope.launch { p.setLockAfter(sec) }
    fun setHideInRecents(v: Boolean) = viewModelScope.launch { p.setHideInRecents(v) }

    // ---- Your data (Privacy screen) --------------------------------------------------
    fun clearSearchHistory() = viewModelScope.launch {
        db.study().clearSearchHistory(); messages.post("Search history cleared")
    }
    fun clearReadingHistory() = viewModelScope.launch {
        db.catalog().clearReadingHistory(); messages.post("Reading history cleared")
    }
    fun clearNotes() = viewModelScope.launch {
        db.study().deleteAllNotes(); db.study().deleteAllBookmarks(); db.catalog().clearAllStatuses()
        messages.post("Notes, bookmarks and progress cleared")
    }
    fun deleteAllDownloads() = viewModelScope.launch {
        val ids = db.downloads().all().map { it.paperId }
        downloads.delete(ids); downloads.cleanDemoAndCache()
        messages.post("Removed ${ids.size} saved papers")
    }

    private val demo get() = mode.value == AppMode.DEMO

    fun pauseQueue() = scheduler.pause()
    fun resumeQueue() = viewModelScope.launch { scheduler.resume(demo) }
    fun retryFailed() = viewModelScope.launch { scheduler.retryFailed(demo) }
    fun cancelQueue() = viewModelScope.launch { scheduler.cancelAll() }
    fun clearFinishedQueue() = viewModelScope.launch { scheduler.clearFinished() }
}
