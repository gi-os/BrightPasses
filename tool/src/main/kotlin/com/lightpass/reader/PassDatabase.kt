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
    val movieTitle: String,
    val theater: String? = null,
    val date: String? = null,       // YYYY-MM-DD
    val time: String? = null,       // h:mm AM/PM
    val seat: String? = null,
    val price: String? = null,
    val code: String? = null,       // barcode/QR text if visible
    val confidence: Double = 0.0,
    val imagePath: String,          // absolute path to the ORIGINAL image
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
        WHERE movieTitle LIKE '%' || :q || '%'
           OR theater LIKE '%' || :q || '%'
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
