package com.gios.lightpass.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.gios.lightpass.util.PassTimes
import java.time.LocalDate

/**
 * Read-only view of the ticket shelf for other tools on the phone.
 *
 * LightNotebook shows a film on the day it screens and opens the ticket here when tapped;
 * without this it would have no way to know, since a ticket is a photo in this app's
 * private storage and nothing else can see it.
 *
 * Exported, but answers only the packages in [ALLOWED_CALLERS]. A signature-level
 * permission would be the usual answer and cannot be used: these tools are sideloaded and
 * each carries its own committed keystore, so no two of them share a signature.
 *
 * Only the fields a calendar needs are exposed — a title, where and when. Not the photo,
 * not the barcode.
 */
class PassProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.$AUTHORITY.pass"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val caller = callingPackage
        if (caller != null && caller !in ALLOWED_CALLERS) return null

        val context = context ?: return null
        val cursor = MatrixCursor(COLUMNS)
        val passes = runCatching {
            PassDatabase.get(context).passDao().allWithDatesBlocking()
        }.getOrDefault(emptyList())

        passes.forEachIndexed { index, pass ->
            val epochDay = runCatching { LocalDate.parse(pass.date) }.getOrNull()?.toEpochDay()
                ?: return@forEachIndexed
            val start = PassTimes.start(pass)
            val startMinutes = start?.let { it.hour * 60 + it.minute }
            // Runtime is only known once a film has been matched on TMDb; without it, an
            // end time would be a guess, and a calendar showing a made-up finish time is
            // worse than one showing none.
            val endMinutes = pass.runtimeMin?.let { runtime ->
                startMinutes?.plus(runtime)?.takeIf { it < MINUTES_IN_DAY }
            }
            cursor.addRow(
                arrayOf<Any?>(
                    index.toLong(),
                    pass.id,
                    pass.movieTitle,
                    pass.theater,
                    pass.seat,
                    epochDay,
                    startMinutes?.toLong(),
                    endMinutes?.toLong(),
                ),
            )
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.gios.lightpass.passes"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/passes")

        private const val MINUTES_IN_DAY = 1440

        private val ALLOWED_CALLERS = setOf(
            "com.gios.lightpass",
            "com.gios.lightnotebook",
        )

        /** Kept in this order for the [MatrixCursor]; consumers read by name. */
        private val COLUMNS = arrayOf(
            "_id",
            "pass_id",
            "title",
            "theater",
            "seat",
            "epoch_day",
            "start_minutes",
            "end_minutes",
        )
    }
}
