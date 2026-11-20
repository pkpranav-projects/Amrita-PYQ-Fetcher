package com.amrita.pyqfetcher.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.amrita.pyqfetcher.core.Config
import com.amrita.pyqfetcher.core.HostMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

enum class ThemeMode(val label: String) { SYSTEM("System"), DARK("Dark / AMOLED"), LIGHT("Light") }
/** Where downloaded papers end up. Chosen per download, the last choice is remembered. */
enum class SaveMode(val label: String, val detail: String) {
    APP_PRIVATE("In the app", "Private library, reads offline anywhere"),
    ALSO_DOWNLOADS("App + phone Downloads", "Also copies each PDF to Downloads/AmritaPYQ"),
    ZIP("One ZIP file", "Library copy plus a single ZIP in Downloads/AmritaPYQ"),
}

data class Prefs(
    val onboarded: Boolean = false,
    val branch: String = Config.DEFAULT_BRANCH,
    val semester: Int = 1,
    val hostMode: HostMode = HostMode.AUTO,
    val demoMode: Boolean = false,
    val pdfInvert: Boolean = false,
    val saveMode: SaveMode = SaveMode.APP_PRIVATE,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val seedVersion: Int = 0,
    val lastSyncAt: Long = 0,
    val autoSync: Boolean = true,
    /** Ask for fingerprint / screen lock when opening the app. */
    val appLock: Boolean = false,
    /** Seconds the app may stay in the background before it locks again (0 = immediately). */
    val lockAfterSec: Int = 60,
    /** Hide app content in the recent-apps screen. */
    val hideInRecents: Boolean = false,
    val notifAsked: Boolean = false,
)

class UserPrefs(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val branch = stringPreferencesKey("branch")
        val semester = intPreferencesKey("semester")
        val hostMode = stringPreferencesKey("host_mode")
        val demo = booleanPreferencesKey("demo_mode")
        val invert = booleanPreferencesKey("pdf_invert")
        val location = stringPreferencesKey("download_location")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val seed = intPreferencesKey("seed_version")
        val lastSync = androidx.datastore.preferences.core.longPreferencesKey("last_sync_at")
        val autoSync = booleanPreferencesKey("auto_sync")
        val synced = androidx.datastore.preferences.core.stringSetPreferencesKey("synced_pages")
        val appLock = booleanPreferencesKey("app_lock")
        val lockAfter = intPreferencesKey("lock_after_sec")
        val hideRecents = booleanPreferencesKey("hide_in_recents")
        val notifAsked = booleanPreferencesKey("notif_asked")
    }

    val flow: Flow<Prefs> = context.dataStore.data.map { p -> p.toPrefs() }

    suspend fun current(): Prefs = flow.first()

    private fun Preferences.toPrefs() = Prefs(
        onboarded = this[K.onboarded] ?: false,
        branch = this[K.branch] ?: Config.DEFAULT_BRANCH,
        semester = this[K.semester] ?: 1,
        hostMode = this[K.hostMode]?.let { v -> HostMode.entries.firstOrNull { it.name == v } } ?: HostMode.AUTO,
        demoMode = this[K.demo] ?: false,
        pdfInvert = this[K.invert] ?: false,
        saveMode = this[K.location]?.let { v -> SaveMode.entries.firstOrNull { it.name == v } }
            ?: SaveMode.APP_PRIVATE,
        themeMode = this[K.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.DARK,
        dynamicColor = this[K.dynamic] ?: false,
        seedVersion = this[K.seed] ?: 0,
        lastSyncAt = this[K.lastSync] ?: 0L,
        autoSync = this[K.autoSync] ?: true,
        appLock = this[K.appLock] ?: false,
        lockAfterSec = this[K.lockAfter] ?: 60,
        hideInRecents = this[K.hideRecents] ?: false,
        notifAsked = this[K.notifAsked] ?: false,
    )

    suspend fun completeOnboarding(branch: String, semester: Int) = context.dataStore.edit {
        it[K.onboarded] = true
        it[K.branch] = branch
        it[K.semester] = semester
    }

    suspend fun setBranch(v: String) = context.dataStore.edit { it[K.branch] = v }
    suspend fun setSemester(v: Int) = context.dataStore.edit { it[K.semester] = v }
    suspend fun setHostMode(v: HostMode) = context.dataStore.edit { it[K.hostMode] = v.name }
    suspend fun setDemoMode(v: Boolean) = context.dataStore.edit { it[K.demo] = v }
    suspend fun setPdfInvert(v: Boolean) = context.dataStore.edit { it[K.invert] = v }
    suspend fun setSaveMode(v: SaveMode) = context.dataStore.edit { it[K.location] = v.name }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[K.dynamic] = v }
    suspend fun setSeedVersion(v: Int) = context.dataStore.edit { it[K.seed] = v }
    suspend fun setLastSyncAt(v: Long) = context.dataStore.edit { it[K.lastSync] = v }
    suspend fun setAutoSync(v: Boolean) = context.dataStore.edit { it[K.autoSync] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[K.appLock] = v }
    suspend fun setLockAfter(v: Int) = context.dataStore.edit { it[K.lockAfter] = v }
    suspend fun setHideInRecents(v: Boolean) = context.dataStore.edit { it[K.hideRecents] = v }
    suspend fun setNotifAsked(v: Boolean) = context.dataStore.edit { it[K.notifAsked] = v }

    /** Forgets which DSpace pages were scanned, so the next check starts fresh. */
    suspend fun clearSyncedPages() = context.dataStore.edit { it.remove(K.synced) }

    /** Session pages whose PDFs were already indexed; later syncs skip them and reach older ones. */
    suspend fun syncedPages(): Set<String> = context.dataStore.data.first()[K.synced] ?: emptySet()
    suspend fun addSyncedPages(pages: Collection<String>) = context.dataStore.edit {
        it[K.synced] = (it[K.synced] ?: emptySet()) + pages
    }
}
