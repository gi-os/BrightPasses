package com.gios.lightpass.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Decoded off the photograph — the cinema's own payload, byte for byte. See [scannedFormat]. */
    val scannedCode: String? = null,
    /** The `BarcodeFormat` name the payload was printed as, so it can be redrawn as itself. */
    val scannedFormat: String? = null,
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

    /** Tickets whose photo has never been scanned for a code. Drives the one-shot backfill. */
    @Query("SELECT * FROM passes WHERE scannedCode IS NULL ORDER BY addedAt DESC")
    suspend fun neverScanned(): List<PassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pass: PassEntity)

    @Update
    suspend fun update(pass: PassEntity)

    @Query("DELETE FROM passes WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [PassEntity::class], version = 3, exportSchema = false)
abstract class PassDatabase : RoomDatabase() {
    abstract fun passDao(): PassDao

    companion object {
        /**
         * The two columns the decoded code lives in.
         *
         * Written as a real migration, and the destructive fallback below is gone with it. Until
         * this version the database was built with `fallbackToDestructiveMigration()`, which means
         * every schema change so far has silently emptied the shelf — survivable while the app was
         * new and nobody had tickets in it, and not survivable now. Adding two nullable columns is
         * the one migration that can't fail: existing rows get null, and null is exactly what a
         * ticket that hasn't been scanned yet should read as.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passes ADD COLUMN scannedCode TEXT")
                db.execSQL("ALTER TABLE passes ADD COLUMN scannedFormat TEXT")
            }
        }

        @Volatile private var INSTANCE: PassDatabase? = null
        fun get(context: Context): PassDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, PassDatabase::class.java, "lightpass.db"
                ).addMigrations(MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
