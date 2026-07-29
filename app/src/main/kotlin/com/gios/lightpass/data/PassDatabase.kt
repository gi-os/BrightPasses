package com.gios.lightpass.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "passes")
data class PassEntity(
    @PrimaryKey val id: String,
    val movieTitle: String,
    val theater: String? = null,
    val date: String? = null,          // YYYY-MM-DD
    val time: String? = null,          // h:mm AM/PM
    val seat: String? = null,
    val price: String? = null,
    val code: String? = null,
    val confidence: Double = 0.0,
    val imagePath: String,             // ORIGINAL (normalized upright) photo
    val croppedPath: String? = null,   // ticket-only crop; falls back to imagePath
    val posterUrl: String? = null,     // TMDb poster
    val overview: String? = null,      // TMDb synopsis
    val runtimeMin: Int? = null,       // TMDb runtime -> end time + auto-archive
    val year: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PassDao {
    @Query("SELECT * FROM passes ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    fun observePass(id: String): Flow<PassEntity?>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PassEntity?

    /**
     * Blocking on purpose: [com.gios.lightpass.data.PassProvider] answers on a binder
     * thread, where a suspend function has no scope to run in.
     */
    @Query("SELECT * FROM passes WHERE date IS NOT NULL ORDER BY date ASC")
    fun allWithDatesBlocking(): List<PassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: PassEntity)

    @Update
    suspend fun update(pass: PassEntity)

    @Query("DELETE FROM passes WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PassEntity::class], version = 2, exportSchema = false)
abstract class PassDatabase : RoomDatabase() {
    abstract fun passDao(): PassDao

    companion object {
        @Volatile private var INSTANCE: PassDatabase? = null
        fun get(context: Context): PassDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, PassDatabase::class.java, "lightpass.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
