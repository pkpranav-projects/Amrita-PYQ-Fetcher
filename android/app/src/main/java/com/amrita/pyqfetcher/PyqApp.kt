package com.amrita.pyqfetcher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.amrita.pyqfetcher.core.AppScope
import com.amrita.pyqfetcher.core.ConnectivityMonitor
import com.amrita.pyqfetcher.data.seed.CatalogSeeder
import com.amrita.pyqfetcher.work.DownloadNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PyqApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: CatalogSeeder
    @Inject lateinit var notifier: DownloadNotifier
    @Inject lateinit var appScope: AppScope
    @Inject lateinit var connectivity: ConnectivityMonitor
    /** Injected so its campus-network watcher starts with the app. */
    @Inject lateinit var syncManager: com.amrita.pyqfetcher.core.SyncManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        // Loads the bundled catalog_seed.json into Room on first launch.
        appScope.scope.launch { runCatching { seeder.seedIfNeeded() } }
    }
}
