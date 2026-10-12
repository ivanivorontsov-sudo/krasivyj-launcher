package org.ivanivorontsov.kodekszhizni.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Article>>

    @Query(
        """
        SELECT * FROM articles
        WHERE title LIKE '%' || :q || '%'
           OR body LIKE '%' || :q || '%'
           OR tags LIKE '%' || :q || '%'
           OR category LIKE '%' || :q || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun search(q: String): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): Article?

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun count(): Int

    @Query("SELECT * FROM articles ORDER BY RANDOM() LIMIT 1")
    suspend fun randomOne(): Article?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: Article): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<Article>)

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
