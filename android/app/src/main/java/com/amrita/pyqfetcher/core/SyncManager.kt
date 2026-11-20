package com.amrita.pyqfetcher.core

import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.repo.LiveRepository
import com.amrita.pyqfetcher.data.repo.SyncProgress
import com.amrita.pyqfetcher.work.DownloadNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class SyncUi(
    val running: Boolean = false,
    val progress: SyncProgress? = null,
    val message: String? = null,
)

/**
 * Runs "find new papers" — on demand from the More screen, and automatically (at most every
 * 12 hours) whenever the phone reaches the campus network, so new uploads show up on their own.
 */
@Singleton
class SyncManager @Inject constructor(
    private val live: LiveRepository,
    private val prefsState: PrefsState,
    private val monitor: ConnectivityMonitor,
    private val appScope: AppScope,
    private val notifier: DownloadNotifier,
    private val db: AppDatabase,
) {
    private val _state = MutableStateFlow(SyncUi())
    val state: StateFlow<SyncUi> = _state.asStateFlow()
    private var job: Job? = null

    init {
        appScope.scope.launch {
            monitor.state.collect { if (it.status == ConnStatus.CAMPUS) maybeAutoSync() }
        }
    }

    private fun maybeAutoSync() {
        val p = prefsState.state.value ?: return
        if (!p.autoSync || !p.onboarded) return
        if (System.currentTimeMillis() - p.lastSyncAt < AUTO_INTERVAL_MS) return
        start(auto = true)
    }

    fun start(auto: Boolean = false) {
        if (_state.value.running) return
        if (monitor.state.value.status != ConnStatus.CAMPUS) {
            _state.value = SyncUi(message = "Finding new papers needs the campus network or VPN.")
            return
        }
        val p = prefsState.state.value ?: return
        job = appScope.scope.launch {
            val started = System.currentTimeMillis()
            _state.value = SyncUi(running = true)
            _state.value = try {
                val r = live.sync(p.branch) { prog -> _state.value = SyncUi(running = true, progress = prog) }
                prefsState.prefs.setLastSyncAt(System.currentTimeMillis())
                val mine = db.catalog().countNewSince(p.branch, p.semester, started)
                if (mine > 0) notifier.newPapers(mine, p.semester)
                SyncUi(
                    message = when {
                        r.newPapers == 0 -> "No new papers found."
                        mine > 0 -> "Found $mine new papers for your semester."
                        else -> "Found ${r.newPapers} new papers, none for Semester ${p.semester}."
                    },
                )
            } catch (e: CancellationException) {
                SyncUi(message = "Stopped")
            } catch (e: Exception) {
                SyncUi(message = if (auto) null else "Couldn't finish: ${e.message}")
            }
        }
    }

    fun stop() { job?.cancel() }

    companion object {
        const val AUTO_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}
