package com.amrita.pyqfetcher.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

private const val PAPER_ROW_SELECT = """
    SELECT p.*, d.localPath AS localPath, d.isDemo AS dlDemo,
           s.code AS subjectCode, s.name AS subjectName, s.legacyCodes AS legacyCodes,
           s.branchCode AS branchCode, s.semester AS semester
    FROM papers p
    JOIN subjects s ON s.id = p.subjectId
    LEFT JOIN downloads d ON d.paperId = p.id
"""

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBranches(items: List<BranchEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubjects(items: List<SubjectEntity>): List<Long>

    /** IGNORE keeps the user's progress (status / last page) when a paper is re-synced. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPapers(items: List<PaperEntity>): List<Long>

    @Query("SELECT * FROM branches ORDER BY sortOrder")
    fun observeBranches(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches ORDER BY sortOrder")
    suspend fun branches(): List<BranchEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun subject(id: String): SubjectEntity?

    @Query("SELECT * FROM subjects")
    suspend fun allSubjects(): List<SubjectEntity>

    @Query("SELECT COUNT(*) FROM papers")
    suspend fun paperCount(): Int

    @Query(
        """
        SELECT s.id, s.branchCode, s.code, s.name, s.semester, s.legacyCodes,
          (SELECT COUNT(*) FROM papers p WHERE p.subjectId = s.id) AS paperCount,
          (SELECT COUNT(*) FROM papers p WHERE p.subjectId = s.id AND p.status = 'SOLVED') AS solvedCount,
          (SELECT COUNT(*) FROM papers p JOIN downloads d ON d.paperId = p.id WHERE p.subjectId = s.id AND (d.isDemo = 0 OR :demo)) AS savedCount
        FROM subjects s
        WHERE s.branchCode = :branch AND (:sem = 0 OR s.semester = :sem)
        ORDER BY s.semester, s.code
        """,
    )
    fun observeSubjects(branch: String, sem: Int, demo: Boolean): Flow<List<SubjectSummary>>

    @Query(
        """
        SELECT s.id, s.branchCode, s.code, s.name, s.semester, s.legacyCodes,
          (SELECT COUNT(*) FROM papers p WHERE p.subjectId = s.id) AS paperCount,
          (SELECT COUNT(*) FROM papers p WHERE p.subjectId = s.id AND p.status = 'SOLVED') AS solvedCount,
          (SELECT COUNT(*) FROM papers p JOIN downloads d ON d.paperId = p.id WHERE p.subjectId = s.id AND (d.isDemo = 0 OR :demo)) AS savedCount
        FROM subjects s ORDER BY s.branchCode, s.semester, s.code
        """,
    )
    fun observeAllSubjects(demo: Boolean): Flow<List<SubjectSummary>>

    @Query("$PAPER_ROW_SELECT WHERE p.subjectId = :subjectId ORDER BY p.year DESC, p.kind, p.title")
    fun observePapers(subjectId: String): Flow<List<PaperRow>>

    @Query("$PAPER_ROW_SELECT WHERE p.subjectId = :subjectId")
    suspend fun papersOf(subjectId: String): List<PaperRow>

    @Query("$PAPER_ROW_SELECT WHERE s.branchCode = :branch AND s.semester = :sem")
    suspend fun papersOfSemester(branch: String, sem: Int): List<PaperRow>

    @Query("$PAPER_ROW_SELECT WHERE s.branchCode = :branch AND s.semester = :sem ORDER BY p.year DESC")
    fun observeSemesterPapers(branch: String, sem: Int): Flow<List<PaperRow>>

    @Query("$PAPER_ROW_SELECT ORDER BY p.year DESC")
    fun observeAllPapers(): Flow<List<PaperRow>>

    @Query("$PAPER_ROW_SELECT WHERE p.id = :id")
    suspend fun paperRow(id: String): PaperRow?

    @Query("$PAPER_ROW_SELECT WHERE p.id = :id")
    fun observePaperRow(id: String): Flow<PaperRow?>

    @Query("$PAPER_ROW_SELECT WHERE p.id IN (:ids)")
    suspend fun paperRows(ids: List<String>): List<PaperRow>

    @Query("$PAPER_ROW_SELECT WHERE p.addedAt > :since AND s.branchCode = :branch AND s.semester = :sem ORDER BY p.addedAt DESC, p.year DESC")
    fun observeNew(branch: String, sem: Int, since: Long): Flow<List<PaperRow>>

    @Query("SELECT COUNT(*) FROM papers p JOIN subjects s ON s.id = p.subjectId WHERE p.addedAt >= :since AND s.branchCode = :branch AND s.semester = :sem")
    suspend fun countNewSince(branch: String, sem: Int, since: Long): Int

    @Query("$PAPER_ROW_SELECT WHERE p.lastOpenedAt > 0 ORDER BY p.lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PaperRow>>

    @Query("UPDATE papers SET lastPage = 0, lastOpenedAt = 0")
    suspend fun clearReadingHistory()

    @Query("UPDATE papers SET status = 'NOT_STARTED'")
    suspend fun clearAllStatuses()

    @Query("UPDATE papers SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE papers SET lastPage = :page, lastOpenedAt = :now WHERE id = :id")
    suspend fun setProgress(id: String, page: Int, now: Long)
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadedPaperEntity)

    @Query("SELECT * FROM downloads WHERE paperId = :id")
    suspend fun get(id: String): DownloadedPaperEntity?

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadedPaperEntity>

    @Query("DELETE FROM downloads WHERE paperId IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads WHERE isDemo = 0")
    fun observeRealBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads WHERE isDemo = 1")
    fun observeDemoBytes(): Flow<Long>

    @Query(
        """
        SELECT p.id AS paperId, p.title AS title, s.id AS subjectId, s.code AS subjectCode, s.name AS subjectName,
               s.branchCode AS branchCode, s.semester AS semester, p.year AS year, p.kind AS kind,
               d.localPath AS localPath, d.sizeBytes AS sizeBytes, d.downloadedAt AS downloadedAt,
               d.isDemo AS isDemo, p.status AS status
        FROM downloads d JOIN papers p ON p.id = d.paperId JOIN subjects s ON s.id = p.subjectId
        ORDER BY d.downloadedAt DESC
        """,
    )
    fun observeVault(): Flow<List<VaultRow>>
}

@Dao
interface StudyDao {
    @Insert
    suspend fun addBookmark(item: BookmarkEntity): Long

    @Delete
    suspend fun deleteBookmark(item: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("SELECT * FROM bookmarks WHERE paperId = :paperId ORDER BY page")
    fun observeBookmarksFor(paperId: String): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT b.id AS id, b.paperId AS paperId, b.page AS page, b.label AS label, b.createdAt AS createdAt,
               p.title AS paperTitle, s.code AS subjectCode
        FROM bookmarks b JOIN papers p ON p.id = b.paperId JOIN subjects s ON s.id = p.subjectId
        ORDER BY b.createdAt DESC
        """,
    )
    fun observeAllBookmarks(): Flow<List<BookmarkRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: PaperNoteEntity)

    @Query("DELETE FROM notes WHERE paperId = :paperId")
    suspend fun deleteNote(paperId: String)

    @Query("SELECT * FROM notes WHERE paperId = :paperId")
    suspend fun note(paperId: String): PaperNoteEntity?

    @Query(
        """
        SELECT n.paperId AS paperId, n.text AS text, n.updatedAt AS updatedAt,
               p.title AS paperTitle, s.code AS subjectCode
        FROM notes n JOIN papers p ON p.id = n.paperId JOIN subjects s ON s.id = p.subjectId
        WHERE n.text <> '' ORDER BY n.updatedAt DESC
        """,
    )
    fun observeAllNotes(): Flow<List<NoteRow>>

    @Insert
    suspend fun addExam(item: ExamScheduleEntity): Long

    @Query("DELETE FROM exam_schedule WHERE id = :id")
    suspend fun deleteExam(id: Long)

    @Query(
        """
        SELECT e.id AS id, e.subjectId AS subjectId, s.code AS subjectCode, s.name AS subjectName,
               e.examType AS examType, e.dateEpochDay AS dateEpochDay, e.note AS note
        FROM exam_schedule e JOIN subjects s ON s.id = e.subjectId
        ORDER BY e.dateEpochDay
        """,
    )
    fun observeExams(): Flow<List<ExamRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSearch(item: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 8")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}

@Dao
interface QueueDao {
    @Insert
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Query("SELECT * FROM queue_items WHERE jobId = :jobId AND state = 'PENDING' ORDER BY position")
    suspend fun pending(jobId: String): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE jobId = :jobId ORDER BY position")
    suspend fun itemsOf(jobId: String): List<QueueItemEntity>

    @Query("UPDATE queue_items SET state = 'PENDING' WHERE jobId = :jobId AND state = 'DOWNLOADING'")
    suspend fun resetDownloading(jobId: String)

    @Query("UPDATE queue_items SET state = :state, error = :error WHERE id = :id")
    suspend fun setState(id: Long, state: String, error: String?)

    @Query("UPDATE queue_items SET state = 'PENDING', error = NULL WHERE state = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("SELECT DISTINCT jobId FROM queue_items WHERE state IN ('PENDING','DOWNLOADING')")
    suspend fun activeJobIds(): List<String>

    @Query("DELETE FROM queue_items WHERE state IN ('DONE','SKIPPED')")
    suspend fun clearFinished()

    @Query("DELETE FROM queue_items")
    suspend fun clearAll()

    @Query("DELETE FROM queue_items WHERE state IN ('PENDING','DOWNLOADING')")
    suspend fun dropPending()

    @Query(
        """
        SELECT q.id AS id, q.jobId AS jobId, q.paperId AS paperId, q.state AS state, q.error AS error,
               p.title AS title, s.code AS subjectCode
        FROM queue_items q JOIN papers p ON p.id = q.paperId JOIN subjects s ON s.id = p.subjectId
        ORDER BY q.id
        """,
    )
    fun observeQueue(): Flow<List<QueueRow>>
}

@Database(
    entities = [
        BranchEntity::class, SubjectEntity::class, PaperEntity::class, DownloadedPaperEntity::class,
        BookmarkEntity::class, PaperNoteEntity::class, ExamScheduleEntity::class,
        SearchHistoryEntity::class, QueueItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun downloads(): DownloadDao
    abstract fun study(): StudyDao
    abstract fun queue(): QueueDao
}

/** v1 → v2: papers remember when a live sync found them (for the "New papers" section). */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE papers ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
    }
}
