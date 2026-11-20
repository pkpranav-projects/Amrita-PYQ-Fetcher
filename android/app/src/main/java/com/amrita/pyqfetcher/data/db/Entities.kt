package com.amrita.pyqfetcher.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val code: String,
    val name: String,
    val description: String,
    val featured: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "subjects", indices = [Index("branchCode"), Index("semester")])
data class SubjectEntity(
    @PrimaryKey val id: String,
    val branchCode: String,
    val code: String,
    val name: String,
    val semester: Int,
    /** Comma-separated legacy course codes bridged by this (2024-curriculum) subject. */
    val legacyCodes: String,
    val isLive: Boolean = false,
)

@Entity(tableName = "papers", indices = [Index("subjectId"), Index("year"), Index("lastOpenedAt")])
data class PaperEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    /** Host-relative DSpace bitstream path, exactly as verified/scraped. */
    val path: String,
    val fileName: String,
    val year: Int,
    /** [com.amrita.pyqfetcher.domain.PaperKind] name. */
    val kind: String,
    val exam: String,
    val sourceCode: String,
    val isLive: Boolean = false,
    /** [com.amrita.pyqfetcher.domain.StudyStatus] name. */
    val status: String = "NOT_STARTED",
    val lastPage: Int = 0,
    val lastOpenedAt: Long = 0,
    /** When a live sync first found this paper (0 for the bundled catalog). */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val addedAt: Long = 0,
)

@Entity(tableName = "downloads")
data class DownloadedPaperEntity(
    @PrimaryKey val paperId: String,
    val localPath: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val isDemo: Boolean,
)

@Entity(tableName = "bookmarks", indices = [Index("paperId")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paperId: String,
    val page: Int,
    val label: String,
    val createdAt: Long,
)

@Entity(tableName = "notes")
data class PaperNoteEntity(
    @PrimaryKey val paperId: String,
    val text: String,
    val updatedAt: Long,
)

@Entity(tableName = "exam_schedule", indices = [Index("subjectId")])
data class ExamScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: String,
    /** "Mid-Term" or "End-Sem". */
    val examType: String,
    val dateEpochDay: Long,
    val note: String = "",
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long,
)

/** One queued file inside a bulk download job. */
@Entity(tableName = "queue_items", indices = [Index("jobId"), Index("state")])
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String,
    val paperId: String,
    /** PENDING, DOWNLOADING, DONE, SKIPPED, FAILED */
    val state: String,
    val error: String? = null,
    val position: Int = 0,
)

// ---- Query result rows -------------------------------------------------------------

data class SubjectSummary(
    val id: String,
    val branchCode: String,
    val code: String,
    val name: String,
    val semester: Int,
    val legacyCodes: String,
    val paperCount: Int,
    val solvedCount: Int,
    val savedCount: Int,
) {
    val legacyList: List<String> get() = legacyCodes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

data class PaperRow(
    @Embedded val paper: PaperEntity,
    val localPath: String?,
    val dlDemo: Boolean?,
    val subjectCode: String,
    val subjectName: String,
    val legacyCodes: String,
    val branchCode: String,
    val semester: Int,
) {
    val isSaved: Boolean get() = localPath != null
}

data class VaultRow(
    val paperId: String,
    val title: String,
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val branchCode: String,
    val semester: Int,
    val year: Int,
    val kind: String,
    val localPath: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
    val isDemo: Boolean,
    val status: String,
)

data class BookmarkRow(
    val id: Long,
    val paperId: String,
    val page: Int,
    val label: String,
    val createdAt: Long,
    val paperTitle: String,
    val subjectCode: String,
)

data class NoteRow(
    val paperId: String,
    val text: String,
    val updatedAt: Long,
    val paperTitle: String,
    val subjectCode: String,
)

data class ExamRow(
    val id: Long,
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val examType: String,
    val dateEpochDay: Long,
    val note: String,
)

data class QueueRow(
    val id: Long,
    val jobId: String,
    val paperId: String,
    val state: String,
    val error: String?,
    val title: String,
    val subjectCode: String,
)
