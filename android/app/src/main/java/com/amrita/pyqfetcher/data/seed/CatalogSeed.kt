package com.amrita.pyqfetcher.data.seed

import android.content.Context
import com.amrita.pyqfetcher.core.Config
import com.amrita.pyqfetcher.data.db.AppDatabase
import com.amrita.pyqfetcher.data.db.BranchEntity
import com.amrita.pyqfetcher.data.db.PaperEntity
import com.amrita.pyqfetcher.data.db.SubjectEntity
import com.amrita.pyqfetcher.data.prefs.UserPrefs
import com.amrita.pyqfetcher.domain.Ids
import com.amrita.pyqfetcher.domain.PaperKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SeedFile(
    val version: Int,
    val branches: List<SeedBranch>,
    val subjects: List<SeedSubject>,
)

@Serializable
data class SeedBranch(val code: String, val name: String, val description: String, val featured: Boolean = false)

@Serializable
data class SeedSubject(
    val code: String,
    val name: String,
    val semester: Int,
    val branch: String,
    val legacyCodes: List<String> = emptyList(),
    val papers: List<SeedPaper> = emptyList(),
)

@Serializable
data class SeedPaper(
    val title: String,
    val path: String,
    val fileName: String,
    val handle: String = "",
    val year: Int,
    val type: String,
    val exam: String,
    val sourceCode: String,
)

data class SeedEntities(
    val branches: List<BranchEntity>,
    val subjects: List<SubjectEntity>,
    val papers: List<PaperEntity>,
)

object SeedMapper {
    val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): SeedFile = json.decodeFromString(SeedFile.serializer(), text)

    fun toEntities(seed: SeedFile): SeedEntities {
        val branches = seed.branches.mapIndexed { i, b ->
            BranchEntity(b.code, b.name, b.description, b.featured, if (b.featured) -1 else i)
        }
        val subjects = ArrayList<SubjectEntity>()
        val papers = ArrayList<PaperEntity>()
        for (s in seed.subjects) {
            val sid = Ids.subjectId(s.branch, s.code)
            subjects.add(SubjectEntity(sid, s.branch, s.code, s.name, s.semester, s.legacyCodes.joinToString(",")))
            for (p in s.papers) {
                papers.add(
                    PaperEntity(
                        id = Ids.paperId(sid, p.path),
                        subjectId = sid,
                        title = p.title,
                        path = p.path,
                        fileName = p.fileName,
                        year = p.year,
                        kind = PaperKind.classify(p.type, p.exam, p.fileName).name,
                        exam = p.exam,
                        sourceCode = p.sourceCode,
                    ),
                )
            }
        }
        return SeedEntities(branches, subjects, papers)
    }
}

/** Loads assets/catalog_seed.json into Room on first launch (and when the seed version bumps). */
class CatalogSeeder(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: UserPrefs,
) {
    suspend fun seedIfNeeded() {
        if (prefs.current().seedVersion >= Config.SEED_VERSION && db.catalog().paperCount() > 0) return
        val text = context.assets.open("catalog_seed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val e = SeedMapper.toEntities(SeedMapper.parse(text))
        val dao = db.catalog()
        dao.insertBranches(e.branches)
        dao.insertSubjects(e.subjects)
        dao.insertPapers(e.papers)
        prefs.setSeedVersion(Config.SEED_VERSION)
    }
}
