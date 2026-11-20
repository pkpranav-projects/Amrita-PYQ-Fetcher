package com.amrita.pyqfetcher.di

import android.content.Context
import androidx.room.Room
import com.amrita.pyqfetcher.core.DSpaceHosts
import com.amrita.pyqfetcher.core.PrefsState
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.CatalogDao
import com.amrita.pyqfetcher.data.db.DownloadDao
import com.amrita.pyqfetcher.data.db.QueueDao
import com.amrita.pyqfetcher.data.db.StudyDao
import com.amrita.pyqfetcher.data.net.DSpaceClient
import com.amrita.pyqfetcher.data.net.PdfDownloader
import com.amrita.pyqfetcher.data.prefs.UserPrefs
import com.amrita.pyqfetcher.data.seed.CatalogSeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    @Provides @Singleton
    fun userPrefs(@ApplicationContext context: Context) = UserPrefs(context)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "amrita_pyq.db")
            .addMigrations(com.amrita.pyqfetcher.data.db.MIGRATION_1_2)
            .build()

    @Provides fun catalogDao(db: AppDatabase): CatalogDao = db.catalog()
    @Provides fun downloadDao(db: AppDatabase): DownloadDao = db.downloads()
    @Provides fun studyDao(db: AppDatabase): StudyDao = db.study()
    @Provides fun queueDao(db: AppDatabase): QueueDao = db.queue()

    @Provides @Singleton
    fun dspaceClient(http: OkHttpClient, prefsState: PrefsState): DSpaceClient =
        DSpaceClient(http, DSpaceHosts()) { prefsState.hostMode() }

    @Provides @Singleton
    fun pdfDownloader(client: DSpaceClient, http: OkHttpClient) = PdfDownloader(client, http)

    @Provides @Singleton
    fun seeder(@ApplicationContext context: Context, db: AppDatabase, prefs: UserPrefs) =
        CatalogSeeder(context, db, prefs)
}
