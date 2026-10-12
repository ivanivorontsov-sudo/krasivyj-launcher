package org.ivanivorontsov.kodekszhizni.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasureDao {
    @Query("SELECT * FROM measures ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<Measure>>

    @Query("SELECT * FROM measures WHERE status = :status ORDER BY startedAt DESC")
    fun observeByStatus(status: MeasureStatus): Flow<List<Measure>>

    @Query("SELECT * FROM measures WHERE id = :id")
    suspend fun getById(id: Long): Measure?

    @Query("SELECT * FROM measures WHERE status = 'ACTIVE' AND endAt IS NOT NULL AND endAt <= :now")
    suspend fun expiredActive(now: Long): List<Measure>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measure: Measure): Long

    @Update
    suspend fun update(measure: Measure)

    @Query("DELETE FROM measures WHERE id = :id")
    suspend fun deleteById(id: Long)
}
