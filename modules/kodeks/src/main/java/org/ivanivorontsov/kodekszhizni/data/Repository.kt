package org.ivanivorontsov.kodekszhizni.data

import kotlinx.coroutines.flow.Flow

class Repository(
    private val articleDao: ArticleDao,
    private val measureDao: MeasureDao
) {
    fun articles(query: String): Flow<List<Article>> =
        if (query.isBlank()) articleDao.observeAll() else articleDao.search(query.trim())

    suspend fun getArticle(id: Long) = articleDao.getById(id)

    suspend fun saveArticle(article: Article): Long {
        return if (article.id == 0L) {
            articleDao.insert(article)
        } else {
            articleDao.update(article.copy(updatedAt = System.currentTimeMillis()))
            article.id
        }
    }

    suspend fun deleteArticle(id: Long) = articleDao.deleteById(id)

    suspend fun randomArticle() = articleDao.randomOne()

    fun measures(): Flow<List<Measure>> = measureDao.observeAll()

    suspend fun getMeasure(id: Long) = measureDao.getById(id)

    suspend fun startMeasure(measure: Measure): Long = measureDao.insert(measure)

    suspend fun stopMeasure(id: Long, completed: Boolean) {
        val m = measureDao.getById(id) ?: return
        measureDao.update(
            m.copy(
                status = if (completed) MeasureStatus.COMPLETED else MeasureStatus.STOPPED,
                completedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMeasure(id: Long) = measureDao.deleteById(id)

    suspend fun completeExpiredMeasures() {
        val now = System.currentTimeMillis()
        measureDao.expiredActive(now).forEach { m ->
            measureDao.update(
                m.copy(status = MeasureStatus.COMPLETED, completedAt = now)
            )
        }
    }
}
