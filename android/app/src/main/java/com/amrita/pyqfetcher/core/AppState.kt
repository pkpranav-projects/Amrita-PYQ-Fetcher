package com.amrita.pyqfetcher.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.amrita.pyqfetcher.data.net.DSpaceClient
import com.amrita.pyqfetcher.data.prefs.Prefs
import com.amrita.pyqfetcher.data.prefs.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppScope @Inject constructor() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** Eagerly-collected copy of the preferences. `null` until DataStore has loaded. */
@Singleton
class PrefsState @Inject constructor(val prefs: UserPrefs, appScope: AppScope) {
    val state: StateFlow<Prefs?> = prefs.flow.stateIn(appScope.scope, SharingStarted.Eagerly, null)
    fun hostMode(): HostMode = state.value?.hostMode ?: HostMode.AUTO
}

enum class ConnStatus { CHECKING, CAMPUS, NO_CAMPUS, NO_NETWORK }

data class ConnState(val status: ConnStatus = ConnStatus.CHECKING, val host: String? = null)

/**
 * Probes dspace.amritanet.edu:8080 (then 172.17.18.57:8080) on launch and whenever the
 * network changes, exposing the result as the live status pill / banner.
 */
@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
    private val client: DSpaceClient,
    private val appScope: AppScope,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _state = MutableStateFlow(ConnState())
    val state: StateFlow<ConnState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
            })
        }
        refresh()
    }

    /** Re-probes (debounced: a newer call cancels a pending one). */
    fun refresh(immediate: Boolean = false) {
        job?.cancel()
        job = appScope.scope.launch {
            if (!immediate) delay(500)
            _state.value = ConnState(ConnStatus.CHECKING, _state.value.host)
            if (cm.activeNetwork == null) {
                _state.value = ConnState(ConnStatus.NO_NETWORK)
                return@launch
            }
            val base = client.probe()
            _state.value = if (base != null) ConnState(ConnStatus.CAMPUS, base) else ConnState(ConnStatus.NO_CAMPUS)
        }
    }
}

/** Resolves the three operating modes: live, offline vault, offline demo. */
@Singleton
class ModeProvider @Inject constructor(
    prefsState: PrefsState,
    monitor: ConnectivityMonitor,
    appScope: AppScope,
) {
    val mode: StateFlow<AppMode> = combine(prefsState.state, monitor.state) { prefs, conn ->
        when {
            conn.status == ConnStatus.CAMPUS -> AppMode.LIVE
            prefs?.demoMode == true -> AppMode.DEMO
            else -> AppMode.OFFLINE_VAULT
        }
    }.stateIn(appScope.scope, SharingStarted.Eagerly, AppMode.OFFLINE_VAULT)
}
