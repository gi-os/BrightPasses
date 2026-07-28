package com.gios.lightpass.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "passes")
data class PassEntity(
    @PrimaryKey val id: String,
    val movieTitle: String,
    val theater: String? = null,
    val date: String? = null,
    val time: String? = null,
    val seat: String? = null,
    val price: String? = null,
    val code: String? = null,
    val confidence: Double = 0.0,
    val imagePath: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface PassDao {
    @Query("SELECT * FROM passes ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    fun observePass(id: String): Flow<PassEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: PassEntity)

    @Query("DELETE FROM passes WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PassEntity::class], version = 1, exportSchema = false)
abstract class PassDatabase : RoomDatabase() {
    abstract fun passDao(): PassDao

    companion object {
        @Volatile private var INSTANCE: PassDatabase? = null
        fun get(context: Context): PassDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, PassDatabase::class.java, "lightpass.db"
                ).build().also { INSTANCE = it }
            }
    }
}
