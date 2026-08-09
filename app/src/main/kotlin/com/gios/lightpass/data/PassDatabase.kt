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
    /** MOVIE, SPORTS or CONCERT — see [EventType]. Changes labels, hides the TMDb plumbing. */
    val eventType: String = EventType.MOVIE,
    /**
     * Tickets to the same showing share a groupId; a lone ticket has null. The group's id is
     * the id of the first ticket that gained a sibling, so it never has to be invented twice.
     */
    val groupId: String? = null,
    /**
     * Generated poster for non-movie passes — the two-crest versus card for a game, the
     * music note for a concert. Each row owns its own file, so deleting one ticket can
     * never blank a sibling's art. Null means show the photo, same as ever.
     */
    val artPath: String? = null,
)

/** The three kinds of thing a ticket can be for. Strings, not an enum — Room stores them raw. */
object EventType {
    const val MOVIE = "MOVIE"
    const val SPORTS = "SPORTS"
    const val CONCERT = "CONCERT"

    val ALL = listOf(MOVIE, SPORTS, CONCERT)

    fun label(type: String): String = when (type) {
        SPORTS -> "Sports"
        CONCERT -> "Concert"
        else -> "Movie"
    }

    /** What the "theater" field should be called for this kind of event. */
    fun venueLabel(type: String): String = if (type == MOVIE) "THEATER" else "VENUE"
}

@Dao
interface PassDao {
    @Query("SELECT * FROM passes ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    fun observePass(id: String): Flow<PassEntity?>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PassEntity?

    /** Every ticket in a group, oldest first — the order they were photographed in. */
    @Query("SELECT * FROM passes WHERE groupId = :groupId ORDER BY addedAt ASC")
    fun observeGroup(groupId: String): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE groupId = :groupId ORDER BY addedAt ASC")
    suspend fun getGroup(groupId: String): List<PassEntity>

    @Query("SELECT * FROM passes")
    suspend fun getAll(): List<PassEntity>

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

@Database(entities = [PassEntity::class], version = 5, exportSchema = false)
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

        /**
         * Event kind + ticket grouping. Same shape as 2→3: additive, defaulted, can't fail.
         * Existing rows become MOVIE tickets in no group — which is exactly what they were.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passes ADD COLUMN eventType TEXT NOT NULL DEFAULT 'MOVIE'")
                db.execSQL("ALTER TABLE passes ADD COLUMN groupId TEXT")
            }
        }

        /** Generated art. Additive and nullable, like every migration before it. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE passes ADD COLUMN artPath TEXT")
            }
        }

        @Volatile private var INSTANCE: PassDatabase? = null
        fun get(context: Context): PassDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, PassDatabase::class.java, "lightpass.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
            }
    }
}
