package com.lightpass.reader

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "passes")
data class PassEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String = "other",      // movie | flight | event | transit | loyalty | other
    val date: String? = null,        // ISO if parsed
    val code: String? = null,        // barcode/QR value if legible
    val issuer: String? = null,
    val imagePath: String,           // absolute path to the ORIGINAL image on disk
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface PassDao {
    @Query("SELECT * FROM passes ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PassEntity>>

    @Query(
        """
        SELECT * FROM passes
        WHERE title LIKE '%' || :q || '%'
           OR type  LIKE '%' || :q || '%'
           OR issuer LIKE '%' || :q || '%'
        ORDER BY addedAt DESC
        """,
    )
    fun observeSearch(q: String): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    fun observePass(id: String): Flow<PassEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: PassEntity)

    @Query("DELETE FROM passes WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(metadata: AppMetadataEntity)

    @Query("SELECT value FROM app_metadata WHERE `key` = :key LIMIT 1")
    suspend fun getMetadata(key: String): String?
}

@Database(
    entities = [PassEntity::class, AppMetadataEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PassDatabase : RoomDatabase() {
    abstract fun passDao(): PassDao
}
